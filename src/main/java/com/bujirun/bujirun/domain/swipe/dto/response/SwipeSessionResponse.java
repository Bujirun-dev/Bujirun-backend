package com.bujirun.bujirun.domain.swipe.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
public class SwipeSessionResponse {
    private UUID sessionId;
    private UUID groupId;
    private String status;
    private int resultCount;
    private LocalDateTime createdAt;
}
