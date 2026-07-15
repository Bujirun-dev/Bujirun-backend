package com.bujirun.bujirun.domain.swipe.dto.request;

import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SwipeSubmitRequest {

    @NotEmpty
    private List<SwipeRequest.SwipeItem> swipes;

    private UUID groupId; // null이면 개인 스와이프
}
