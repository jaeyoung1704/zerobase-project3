package com.zerobase.dividend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.zerobase.dividend.service.MemberService;

import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
public class TokenProvider {

    private static final long TOKEN_EXPIRE_TIME = 1000 * 60 * 60; // 1 hour
    private static final String KEY_ROLES = "roles";

    private final MemberService memberService;

    @Value("{spring.jwt.secret}")
    private String secretKey;

    /**
     * 토큰 생성(발급)
     * 
     * @param username
     * @param roles
     * @return
     */
    public String generateToken(String username, List<String> roles) {
	// 다음 정보들을 포함한 claims 생성
	// - username
	// - roles
	// - 생성 시간
	// - 만료 시간
	// - signature

	// jwt 발급
	Claims claims = Jwts.claims().setSubject(username);
	claims.put(KEY_ROLES, roles);
	var now = new Date();
	var expiredDate = new Date(now.getTime() + TOKEN_EXPIRE_TIME);
	return Jwts.builder()
	    .setClaims(claims)
	    .setIssuedAt(now)
	    .setExpiration(expiredDate)
	    .signWith(SignatureAlgorithm.HS512, secretKey)
	    .compact();
    }

    public Authentication getAuthentication(String jwt) {
	UserDetails userDetails =
	    this.memberService.loadUserByUsername(this.getUsername(jwt));
	return new UsernamePasswordAuthenticationToken(userDetails, "",
	    userDetails.getAuthorities());
    }

    public String getUsername(String token) {
	return this.parseClaims(token).getSubject();
    }

    // 토큰이 유효한지 확인
    public boolean validateToken(String token) {
	if (!StringUtils.hasText(token))
	    return false;

	var claims = this.parseClaims(token);
	return !claims.getExpiration().before(new Date());
    }

    // 토큰으로부터 정보 파싱
    private Claims parseClaims(String token) {
	try {
	    return Jwts.parser()
		.setSigningKey(this.secretKey)
		.parseClaimsJws(token)
		.getBody();
	}
	catch (ExpiredJwtException e) {
	    return e.getClaims();
	}
    }

}
