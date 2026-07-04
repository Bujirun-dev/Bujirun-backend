package com.bujirun.bujirun.domain.group.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;

public record GroupMemberResponse(
        UUID userId,
        String nickname,
        LocalDateTime joinedAt
) {}
