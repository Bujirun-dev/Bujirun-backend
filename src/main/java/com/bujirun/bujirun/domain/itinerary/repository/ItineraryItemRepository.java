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

    @Query("SELECT i.id FROM ItineraryItem i WHERE i.spot.category = :category")
    List<UUID> findIdsBySpotCategory(@Param("category") String category);
}
