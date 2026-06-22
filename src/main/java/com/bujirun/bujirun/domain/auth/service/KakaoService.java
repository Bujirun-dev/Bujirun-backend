package com.bujirun.bujirun.domain.auth.service;

import com.bujirun.bujirun.domain.auth.config.KakaoConfig;
import com.bujirun.bujirun.domain.auth.dto.KakaoTokenResponse;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.global.jwt.JwtProvider;
import com.bujirun.bujirun.global.jwt.JwtProperties;
import com.bujirun.bujirun.global.jwt.RefreshTokenRepository;
import com.bujirun.bujirun.global.jwt.dto.TokenResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Service
@RequiredArgsConstructor
public class KakaoService {

    private final KakaoConfig kakaoConfig;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;                    // 만료 시간 가져오려고 추가
    private final RefreshTokenRepository refreshTokenRepository;  // Redis 저장소 추가

    private static final String TOKEN_REQUEST_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_REQUEST_URL = "https://kapi.kakao.com/v2/user/me";

    // 1단계: 인가 코드로 액세스 토큰 받기
    public KakaoTokenResponse getToken(String code) {

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", kakaoConfig.getClientId());
        formData.add("redirect_uri", kakaoConfig.getRedirectUri());
        formData.add("client_secret", kakaoConfig.getClientSecret());
        formData.add("code", code);

        RestClient restClient = RestClient.create();

        return restClient.post()
                .uri(TOKEN_REQUEST_URL)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(formData)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    // 2단계: 액세스 토큰으로 사용자 정보 받기
    public KakaoUserInfoResponse getUserInfo(String accessToken) {

        RestClient restClient = RestClient.create();

        return restClient.get()
                .uri(USER_INFO_REQUEST_URL)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .retrieve()
                .body(KakaoUserInfoResponse.class);
    }

    // 3단계: 기존 회원이면 조회, 아니면 신규 가입
    public User findOrCreateUser(KakaoUserInfoResponse userInfo) {

        String providerId = String.valueOf(userInfo.getId());

        return userRepository.findByProviderIdAndAuthProvider(providerId, "kakao")
                .orElseGet(() -> userRepository.save(
                        User.builder()
                                .nickname(userInfo.getKakaoAccount().getProfile().getNickname())
                                .email(userInfo.getKakaoAccount().getEmail())
                                .authProvider("kakao")
                                .providerId(providerId)
                                .build()
                ));
    }

    // 4단계: Access Token 발급
    public TokenResponse createToken(User user) {
        return jwtProvider.createTokenResponse(user.getId());
    }

    // Refresh Token 생성 + Redis에 저장
    public String createRefreshToken(User user) {
        String refreshToken = jwtProvider.createRefreshToken(user.getId());
        refreshTokenRepository.save(
                user.getId(),
                refreshToken,
                jwtProperties.getRefreshTokenExpiration() // 만료 시간(2주) 함께 저장
        );
        return refreshToken;
    }
}