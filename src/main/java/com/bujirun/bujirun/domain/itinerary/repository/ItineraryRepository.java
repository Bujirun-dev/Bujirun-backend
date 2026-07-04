package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    List<Itinerary> findByUserIdOrderByCreatedAtDesc(UUID userId);

    List<Itinerary> findByGroupIdInOrderByCreatedAtDesc(List<UUID> groupIds);
}
