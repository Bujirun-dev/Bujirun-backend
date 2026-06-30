package com.bujirun.bujirun.domain.collection.repository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface CollectionListProjection {
    UUID getSpotId();
    String getName();
    Integer getSigunguId();
    String getThumbnailUrl();
    Boolean getCollected();      // boolean → Boolean
    LocalDateTime getCollectedAt();
}