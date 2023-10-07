package com.zerobase.dividend.model;

import lombok.Data;

import java.util.List;

import com.zerobase.dividend.persist.entity.MemberEntity;

public class Auth {

    @Data // 로그인용
    public static class SignIn {
	private String username;
	private String password;
    }

    @Data // 회원가입용
    public static class SignUp {
	private String username;
	private String password;
	private List<String> roles;

	public MemberEntity toEntity() {
	    return MemberEntity.builder()
		.username(this.username)
		.password(this.password)
		.roles(this.roles)
		.build();
	}
    }
}
