package com.bujirun.bujirun.domain.group.dto.response;

import java.util.UUID;

public record GroupInvitePreviewResponse(
        String groupName,
        String inviterNickname,
        long memberCount,
        boolean completed,
        UUID itineraryId
) {
}
