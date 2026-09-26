package com.bujirun.bujirun.global.internal;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

// /api/internal/** 전용 서버 간 인증. JWT가 아니라 고정 비밀값(X-Internal-Secret 헤더)을
// INTERNAL_API_SECRET과 비교한다 — node-yjs 서버가 room의 flush를 대신 호출할 때 쓰며,
// 이 경로는 사람이 로그인해서 호출하는 게 아니라 우리 인프라 안의 다른 서버가 호출하는
// 것이므로 JwtAuthFilter/JWT 발급 흐름과는 완전히 분리한다(SecurityConfig의 별도
// SecurityFilterChain 참고).
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Internal-Secret";

    @Value("${internal.api.secret:}")
    private String expectedSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        // expectedSecret이 비어있으면(설정 누락) 절대 통과시키지 않는다 — 빈 문자열 헤더나
        // 헤더 미전송(null)이 빈 expectedSecret과 우연히 "일치"해버리는 사고를 막기 위함.
        if (!expectedSecret.isBlank() && expectedSecret.equals(provided)) {
            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    "internal-service",
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_INTERNAL"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }
}
