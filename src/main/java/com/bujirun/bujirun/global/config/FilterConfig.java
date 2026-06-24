package com.bujirun.bujirun.global.config;

import com.bujirun.bujirun.global.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// JWT 필터를 스프링에 등록하는 설정
@Configuration
@RequiredArgsConstructor
public class FilterConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(jwtAuthFilter);
        registrationBean.addUrlPatterns("/api/*"); // /api/* 경로에만 필터 적용
        return registrationBean;
    }
}