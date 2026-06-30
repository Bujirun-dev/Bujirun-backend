package com.bujirun.bujirun.domain.collection.repository;

import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;
import com.bujirun.bujirun.domain.collection.entity.CollectionEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionEntryRepository extends JpaRepository<CollectionEntry, CollectionEntryId> {

    Optional<CollectionEntry> findByUserIdAndSpotId(UUID userId, UUID spotId);

    List<CollectionEntry> findByUserIdAndCollectedTrue(UUID userId);

    @Query("""
        select ts.id as spotId, ts.name as name, ts.sigungu.id as sigunguId,
               ts.thumbnailUrl as thumbnailUrl, ce.collected as collected, ce.collectedAt as collectedAt
        from TourSpot ts
        left join CollectionEntry ce on ce.spot = ts and ce.user.id = :userId
        where ts.collection = true
        """)
    List<CollectionListProjection> findCollectionBoard(@Param("userId") UUID userId);
}