package com.bujirun.bujirun.domain.auth.service;

import com.bujirun.bujirun.domain.auth.config.KakaoConfig;
import com.bujirun.bujirun.domain.auth.dto.KakaoTokenResponse;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.dto.UserAuthResult;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;
@Service
@RequiredArgsConstructor
public class KakaoService {

    private final KakaoConfig kakaoConfig;
    private final UserRepository userRepository;
    private final JwtProvider jwtProvider;
    private final JwtProperties jwtProperties;
    private final RefreshTokenRepository refreshTokenRepository;

    private static final String TOKEN_REQUEST_URL = "https://kauth.kakao.com/oauth/token";
    private static final String USER_INFO_REQUEST_URL = "https://kapi.kakao.com/v2/user/me";

    // 1단계: 인가 코드로 액세스 토큰 받기
    public KakaoTokenResponse getToken(String code) {

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "authorization_code");
        formData.add("client_id", kakaoConfig.getClientId());
        formData.add("client_secret", kakaoConfig.getClientSecret());
        formData.add("redirect_uri", kakaoConfig.getRedirectUri());
        formData.add("code", code);

        RestClient restClient = RestClient.create();

        try {
            return restClient.post()
                    .uri(TOKEN_REQUEST_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(KakaoTokenResponse.class);
        } catch (HttpClientErrorException e) {
            throw new IllegalArgumentException("유효하지 않은 카카오 인가 코드입니다", e);
        }
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
    // isNewUser 플래그로 프론트가 신규 가입자에게 닉네임/프로필사진 설정 화면을 보여줄지 판단.
    @Transactional
    public UserAuthResult findOrCreateUser(KakaoUserInfoResponse userInfo) {

        String providerId = String.valueOf(userInfo.getId());
        String nickname = userInfo.getKakaoAccount().getProfile().getNickname();
        String profileImageUrl = userInfo.getKakaoAccount().getProfile().getProfileImageUrl();
        String email = userInfo.getKakaoAccount().getEmail();

        // 탈퇴한 유저인지 먼저 확인
        Optional<User> deletedUser = userRepository.findByProviderIdAndAuthProvider(providerId, "kakao")
                .filter(User::isDeleted);

        if (deletedUser.isPresent()) {
            User user = deletedUser.get();

            if (user.getDeletedAt().isAfter(LocalDateTime.now().minusDays(30))) {
                // 30일 이내 재가입 → 계정 복구
                user.restore();
                return new UserAuthResult(user, false);
            } else {
                // 30일 초과 → 기존 탈퇴 계정 provider_id 초기화 후 새 유저 생성
                user.clearProviderId();
                userRepository.saveAndFlush(user); // 이 줄 추가
                return new UserAuthResult(
                        userRepository.save(
                                User.builder()
                                        .nickname(nickname)
                                        .profileImageUrl(profileImageUrl)
                                        .email(email)
                                        .authProvider("kakao")
                                        .providerId(providerId)
                                        .build()
                        ),
                        true
                );
            }
        }

        // 정상 유저면 조회, 없으면 신규 가입
        return userRepository.findByProviderIdAndAuthProviderAndDeletedAtIsNull(providerId, "kakao")
                .map(user -> new UserAuthResult(user, false))
                .orElseGet(() -> new UserAuthResult(
                        userRepository.save(
                                User.builder()
                                        .nickname(nickname)
                                        .profileImageUrl(profileImageUrl)
                                        .email(email)
                                        .authProvider("kakao")
                                        .providerId(providerId)
                                        .build()
                        ),
                        true
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
                jwtProperties.getRefreshTokenExpiration()
        );
        return refreshToken;
    }
}