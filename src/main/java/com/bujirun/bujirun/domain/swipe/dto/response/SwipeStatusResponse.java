package com.bujirun.bujirun.domain.swipe.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SwipeStatusResponse {
    private long doneCount;
    private long totalCount;
    private boolean allDone;
}
