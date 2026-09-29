package com.bujirun.bujirun.global.config;

import com.bujirun.bujirun.global.internal.InternalAuthFilter;
import com.bujirun.bujirun.global.jwt.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final InternalAuthFilter internalAuthFilter;

    // /api/internal/**: node-yjs 같은 내부 서버가 호출하는 경로. 사람 로그인(JWT)과는
    // 완전히 다른 인증 수단(X-Internal-Secret)을 쓰므로, JwtAuthFilter가 걸린 기본 체인과
    // 아예 별도의 SecurityFilterChain으로 분리한다 — 한 체인에 필터만 추가하면
    // "JWT도 있어야 하고 내부 시크릿도 있어야 함" 같은 의도치 않은 AND 조건이 되거나,
    // 반대로 이 경로에서 JwtAuthFilter가 앞서 다른 인증을 채워 넣어버릴 수 있다.
    // matcher가 있는 체인은 matcher가 없는 체인보다 먼저 평가돼야 하므로 @Order(1).
    @Bean
    @Order(1)
    public SecurityFilterChain internalFilterChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/api/internal/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"내부 인증이 필요합니다.\",\"data\":null}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                .addFilterBefore(internalAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value()); // 401
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"인증이 필요합니다.\",\"data\":null}"
                            );
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value()); // 403 (진짜 권한 부족일 때)
                            response.setCharacterEncoding("UTF-8");
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    "{\"success\":false,\"message\":\"접근 권한이 없습니다.\",\"data\":null}"
                            );
                        })
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        // 초대 링크 미리보기: 카카오톡 공유 크롤러/OG 메타태그 생성 등 비로그인 상태에서도 호출됨
                        .requestMatchers(HttpMethod.GET, "/api/groups/invites/*/preview").permitAll()
                        // GlobalExceptionHandler가 못 잡는 예외(예: 요청 바디 자체를 역직렬화하다
                        // 실패하는 HttpMessageNotReadableException)는 서블릿 컨테이너가 자동으로
                        // /error로 forward한다. 이 forward는 원래 요청 경로와 무관하게 항상 이 체인
                        // (매처가 없어 "나머지 전부"를 받음)으로 들어오는데, /api/internal/**에서
                        // 이 forward가 발생하면 인증 수단이 JWT가 아니라서 진짜 에러(400 등) 대신
                        // "인증이 필요합니다"(401)로 뒤바뀌어 원인을 알 수 없게 된다(로컬에서 실제
                        // 재현·확인, 2026-09-17). /error 응답은 이미 결정된 상태/본문을 그대로
                        // 돌려주는 것뿐이라 permitAll이어도 안전하다.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    // CORS 정책 — 프론트(localhost:3000) 요청 및 쿠키 허용
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://bujirun.store",
                "https://api.bujirun.store",
                "https://bujirun-frontend.vercel.app"  // ← 추가
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // refresh_token 쿠키 전달에 필수
        config.setMaxAge(3600L);          // preflight 캐시 1시간

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}