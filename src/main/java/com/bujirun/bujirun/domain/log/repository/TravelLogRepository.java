package com.bujirun.bujirun.domain.log.repository;

import com.bujirun.bujirun.domain.log.entity.TravelLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TravelLogRepository extends JpaRepository<TravelLog, UUID> {
    boolean existsByItineraryId(UUID itineraryId);
    List<TravelLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<TravelLog> findByIsPublicTrueOrderByCreatedAtDesc();
    List<TravelLog> findByIsPublicTrueOrderByAddedCountDesc();
    List<TravelLog> findByIdInAndIsPublicTrueOrderByCreatedAtDesc(Collection<UUID> ids);
    List<TravelLog> findByIdInAndIsPublicTrueOrderByAddedCountDesc(Collection<UUID> ids);
}
