package com.bujirun.bujirun.domain.swipe.dto.projection;

import java.util.UUID;

public interface UserSwipeAggregate {
    UUID getUserId();
    Long getLikedCount();
    Long getTotalCount();
}
