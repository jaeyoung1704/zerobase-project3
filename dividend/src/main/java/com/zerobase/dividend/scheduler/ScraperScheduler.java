package com.zerobase.dividend.scheduler;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.zerobase.dividend.model.Company;
import com.zerobase.dividend.model.ScrapedResult;
import com.zerobase.dividend.model.constants.CacheKey;
import com.zerobase.dividend.persist.CompanyRepository;
import com.zerobase.dividend.persist.DividendRepository;
import com.zerobase.dividend.persist.entity.CompanyEntity;
import com.zerobase.dividend.persist.entity.DividendEntity;
import com.zerobase.dividend.scraper.Scraper;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@EnableCaching
@AllArgsConstructor
public class ScraperScheduler {

    private final CompanyRepository companyRepository;
    private final DividendRepository dividendRepository;

    private final Scraper yahooFinanceScraper;

    @CacheEvict(value = CacheKey.KEY_FINANCE, allEntries = true)
    @Scheduled(cron = "${scheduler.scrap.yahoo}")
    public void yahooFinanceScheduling() {
	log.info("scraping scheduler is started");
	// 저장된 회사 목록을 조회
	List<CompanyEntity> companies = this.companyRepository.findAll();

	// 회사마다 배당금 정보를 새로 스크래핑
	for (var company : companies) {
	    log.info("scraping scheduler -> " + company.getName());
	    ScrapedResult scrapedResult = this.yahooFinanceScraper.scrap(
		new Company(company.getTicker(), company.getName()));

	    // 스크래핑한 배당금 정보 중 데이터베이스에 없는 값은 저장
	    scrapedResult.getDividends()
		.stream()
		// 디비든 모델을 디비든 엔티티로 매핑
		.map(e -> new DividendEntity(company.getId(), e))
		// 엘리먼트를 하나씩 디비든 레파지토리에 삽입(존재하지 않는경우에만)
		.forEach(e -> {
		    boolean exists = dividendRepository
			.existsByCompanyIdAndDate(e.getCompanyId(), e.getDate());
		    if (!exists) {
			dividendRepository.save(e);
			log.info("insert new dividend -> " + e.toString());
		    }
		});

	    // 연속적으로 스크래핑 대상 사이트 서버에 요청을 날리지 않도록 일시정지
	    try {
		Thread.sleep(3000); // 3 seconds
	    }
	    catch (InterruptedException e) {
		Thread.currentThread().interrupt();
	    }

	}

    }
}
