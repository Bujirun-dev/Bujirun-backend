package com.bujirun.bujirun.domain.group.dto.response;

public record GroupInvitePreviewResponse(
        String groupName,
        String inviterNickname,
        long memberCount
) {
}