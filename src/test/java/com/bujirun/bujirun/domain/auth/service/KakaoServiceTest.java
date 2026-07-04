package com.bujirun.bujirun.domain.auth.service;

import com.bujirun.bujirun.domain.auth.config.KakaoConfig;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.dto.UserAuthResult;
import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.global.jwt.JwtProperties;
import com.bujirun.bujirun.global.jwt.JwtProvider;
import com.bujirun.bujirun.global.jwt.RefreshTokenRepository;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class KakaoServiceTest {

    private final KakaoConfig kakaoConfig = mock(KakaoConfig.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final JwtProvider jwtProvider = mock(JwtProvider.class);
    private final JwtProperties jwtProperties = mock(JwtProperties.class);
    private final RefreshTokenRepository refreshTokenRepository = mock(RefreshTokenRepository.class);

    private final KakaoService kakaoService = new KakaoService(
            kakaoConfig, userRepository, jwtProvider, jwtProperties, refreshTokenRepository);

    @Test
    void 기존_회원이면_isNewUser가_false() throws Exception {
        KakaoUserInfoResponse userInfo = kakaoUserInfo(12345L, "홍길동", "http://img", "test@test.com");
        User existing = User.builder().nickname("홍길동").authProvider("kakao").providerId("12345").build();
        when(userRepository.findByProviderIdAndAuthProvider("12345", "kakao"))
                .thenReturn(Optional.of(existing));

        UserAuthResult result = kakaoService.findOrCreateUser(userInfo);

        assertThat(result.isNewUser()).isFalse();
        assertThat(result.user()).isSameAs(existing);
        verify(userRepository, never()).save(any());
    }

    @Test
    void 신규_회원이면_isNewUser가_true이고_카카오_프로필로_생성() throws Exception {
        KakaoUserInfoResponse userInfo = kakaoUserInfo(999L, "새유저", "http://new-img", "new@test.com");
        when(userRepository.findByProviderIdAndAuthProvider("999", "kakao"))
                .thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        UserAuthResult result = kakaoService.findOrCreateUser(userInfo);

        assertThat(result.isNewUser()).isTrue();
        assertThat(result.user().getNickname()).isEqualTo("새유저");
        assertThat(result.user().getProfileImageUrl()).isEqualTo("http://new-img");
        verify(userRepository).save(any(User.class));
    }

    // KakaoUserInfoResponse/KakaoAccount/Profile은 전부 @NoArgsConstructor(private field)라
    // 리플렉션으로 값을 채워 테스트용 응답 객체를 만든다.
    private KakaoUserInfoResponse kakaoUserInfo(Long id, String nickname, String profileImageUrl, String email) throws Exception {
        KakaoUserInfoResponse.Profile profile = new KakaoUserInfoResponse.Profile();
        setField(profile, "nickname", nickname);
        setField(profile, "profileImageUrl", profileImageUrl);

        KakaoUserInfoResponse.KakaoAccount account = new KakaoUserInfoResponse.KakaoAccount();
        setField(account, "email", email);
        setField(account, "profile", profile);

        KakaoUserInfoResponse response = new KakaoUserInfoResponse();
        setField(response, "id", id);
        setField(response, "kakaoAccount", account);
        return response;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }
}
