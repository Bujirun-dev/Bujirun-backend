package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItineraryItemRepository extends JpaRepository<ItineraryItem, UUID> {
}
