package com.zerobase.dividend.service;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.Trie;
import org.apache.commons.collections4.trie.PatriciaTrie;
import org.hibernate.cfg.NotYetImplementedException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import com.zerobase.dividend.exception.impl.NoCompanyException;
import com.zerobase.dividend.model.Company;
import com.zerobase.dividend.model.ScrapedResult;
import com.zerobase.dividend.model.constants.CacheKey;
import com.zerobase.dividend.persist.CompanyRepository;
import com.zerobase.dividend.persist.DividendRepository;
import com.zerobase.dividend.persist.entity.CompanyEntity;
import com.zerobase.dividend.persist.entity.DividendEntity;
import com.zerobase.dividend.scraper.Scraper;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@AllArgsConstructor
public class CompanyService {

    private final Trie<String, String> trie;
    private final Scraper yahooFinanceScraper;
    private final CompanyRepository companyRepository;
    private final DividendRepository dividendRepository;

    public Company save(String ticker) {
	// 이미 등록된 ticker인지 확인
	boolean exists = companyRepository.existsByTicker(ticker);
	if (exists)
	    throw new RuntimeException("already exists ticker ->" + ticker);
	return storeCompanyAndDividend(ticker);
    }

    public Page<CompanyEntity> getAllCompany(Pageable pageable) {
	return companyRepository.findAll(pageable);
    }

    private Company storeCompanyAndDividend(String ticker) {
	// 1. ticker 를 기준으로 회사를 스크래핑
	Company company = yahooFinanceScraper.scrapCompanyByTicker(ticker);

	// 올바르지 않은 ticker면 에러발생
	if (ObjectUtils.isEmpty(company))
	    throw new RuntimeException("failed to scrap ticker ->" + ticker);

	// 2. 해당 회사가 존재할 경우, 회사의 배당금 정보를 스크래핑
	ScrapedResult scrapedResult = yahooFinanceScraper.scrap(company);

	// 3. 스크래핑 결과 반환
	CompanyEntity companyEntity =
	    companyRepository.save(new CompanyEntity(company));
	List<DividendEntity> dividendEntitList = scrapedResult.getDividends()
	    .stream()
	    .map(e -> new DividendEntity(companyEntity.getId(), e))
	    .collect(Collectors.toList());
	dividendRepository.saveAll(dividendEntitList);
	return company;
    }

    // 자동완성 검색 - db like 사용
    public Page<CompanyEntity> getCompanyNamesByKeyword(String keyword) {
	Pageable limit = PageRequest.of(0, 10);
	return companyRepository.findByNameStartingWithIgnoreCase(keyword, limit);
    }

    // 자동완성 키워드에 등록
    public void addAutocompleteKeyword(String keyword) {
	trie.put(keyword, null);
    }

    // 자동완성 검색- trie 사용
    public List<String> autocomplete(String keyword) {
	return (List<String>) trie.prefixMap(keyword)
	    .keySet()
	    .stream()
	    .limit(10)
	    .collect(Collectors.toList());
    }

    public void deleteAutocompleteKeyword(String keyword) {
	trie.remove(keyword);
    }

    public String deleteCompany(String ticker) {
	// 우선 ticker로 회사정보 조회
	CompanyEntity company = companyRepository.findByTicker(ticker)
	    .orElseThrow(() -> new NoCompanyException());
	// 1. 배당금 정보 삭제
	dividendRepository.deleteAllByCompanyId(company.getId());
	// 2. 회사 정보 삭제
	companyRepository.delete(company);
	// 자동완성 정보 삭제
	deleteAutocompleteKeyword(company.getName());
	// 캐시 삭제를 위해 삭제된 회사이름 반환
	return company.getName();
    }

}
