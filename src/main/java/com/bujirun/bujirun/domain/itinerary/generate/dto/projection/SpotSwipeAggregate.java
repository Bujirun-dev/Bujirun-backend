package com.bujirun.bujirun.domain.itinerary.generate.dto.projection;

import java.util.UUID;

public interface SpotSwipeAggregate {
    UUID getSpotId();
    Long getLikedCount();
    Long getTotalCount();
}