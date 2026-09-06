package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {

    @Query("SELECT i.id FROM ItineraryItem i WHERE i.spot.id = :spotId")
    List<UUID> findIdsBySpotId(@Param("spotId") UUID spotId);

    // category는 프론트 필터칩(바다/자연/문화/체험 = 도감 4분류)에서 오므로, TourAPI 원본
    // 카테고리(spot.category)가 아니라 도감 분류 컬럼(spot.collectionCategory)으로 매칭해야 한다.
    @Query("SELECT i.id FROM ItineraryItem i WHERE i.spot.collectionCategory = :category")
    List<UUID> findIdsBySpotCategory(@Param("category") String category);
}
