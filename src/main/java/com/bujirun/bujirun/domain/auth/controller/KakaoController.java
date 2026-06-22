package com.bujirun.bujirun.domain.auth.controller;

import com.bujirun.bujirun.domain.auth.dto.KakaoTokenResponse;
import com.bujirun.bujirun.domain.auth.dto.KakaoUserInfoResponse;
import com.bujirun.bujirun.domain.auth.entity.User;  // ← ① import 추가
import com.bujirun.bujirun.domain.auth.service.KakaoService;
import com.bujirun.bujirun.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class KakaoController {

    private final KakaoService kakaoService;

    // 카카오 로그인 콜백 엔드포인트
    // - 카카오 콘솔에 등록한 리다이렉트 URI와 경로가 정확히 일치해야 함
    // - 사용자가 동의 화면에서 [동의하고 계속하기]를 누르면 카카오가 이 URL로 code를 실어서 호출함
    @GetMapping("/api/auth/kakao/callback")
    public ApiResponse<User> kakaoCallback(@RequestParam("code") String code) {  // ← ② 반환 타입 변경

        // 1단계: 인가 코드(code) -> 카카오 액세스 토큰으로 교환
        KakaoTokenResponse tokenResponse = kakaoService.getToken(code);

        // 2단계: 액세스 토큰 -> 사용자 정보(닉네임, 이메일 등) 조회
        KakaoUserInfoResponse userInfo = kakaoService.getUserInfo(tokenResponse.getAccessToken());

        // 3단계: DB에서 기존 회원 조회, 없으면 신규 가입
        User user = kakaoService.findOrCreateUser(userInfo);  // ← ③ 이 줄 추가, TODO 주석 삭제

        return ApiResponse.ok(user);  // ← ④ userInfo → user로 변경
    }
}