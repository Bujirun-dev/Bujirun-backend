package com.bujirun.bujirun.domain.auth.dto.request;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 50, message = "닉네임은 1자 이상 50자 이하로 입력해주세요.")
        String nickname,

        @Size(max = 500, message = "프로필 이미지 URL이 너무 깁니다.")
        String profileImageUrl
) {}
