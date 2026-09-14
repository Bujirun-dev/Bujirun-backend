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

    // category는 프론트 필터칩(바다/자연/문화/체험)에서 오므로, TourAPI 원본 카테고리(spot.category, 6분류)가
    // 아니라 통합 4분류 컬럼(spot.spotCategory)으로 매칭해야 한다. spotCategory는 도감 외 스팟에도 채워져 있어
    // collection = true 조건을 명시해 기존처럼 도감 스팟으로만 필터 범위를 유지한다.
    @Query("SELECT i.id FROM ItineraryItem i WHERE i.spot.collection = true AND i.spot.spotCategory = :category")
    List<UUID> findIdsBySpotCategory(@Param("category") String category);
}
