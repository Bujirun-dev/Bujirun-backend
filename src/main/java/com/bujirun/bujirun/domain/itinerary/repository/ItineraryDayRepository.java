package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    boolean existsByItineraryIdAndDayNumber(UUID itineraryId, int dayNumber);
}
