package com.bujirun.bujirun.domain.auth.dto.response;

import com.bujirun.bujirun.domain.auth.entity.User;

import java.util.UUID;

public record UserProfileResponse(
        UUID id,
        String nickname,
        String profileImageUrl,
        String email
) {
    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getNickname(),
                user.getProfileImageUrl(),
                user.getEmail()
        );
    }
}
