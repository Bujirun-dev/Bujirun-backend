package com.bujirun.bujirun.domain.group.dto.response;

import com.bujirun.bujirun.domain.group.entity.Group;

import java.time.LocalDateTime;
import java.util.UUID;

public record GroupResponse(
        UUID id,
        String name,
        String inviteCode,
        UUID createdBy,
        LocalDateTime createdAt
) {
    public static GroupResponse from(Group group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getInviteCode(),
                group.getCreatedBy(),
                group.getCreatedAt()
        );
    }
}
