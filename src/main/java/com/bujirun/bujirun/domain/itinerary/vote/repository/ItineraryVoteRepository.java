package com.bujirun.bujirun.domain.itinerary.vote.repository;

import com.bujirun.bujirun.domain.itinerary.vote.entity.ItineraryVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryVoteRepository extends JpaRepository<ItineraryVote, UUID> {

    List<ItineraryVote> findBySessionId(UUID sessionId);

    Optional<ItineraryVote> findBySessionIdAndUserId(UUID sessionId, UUID userId);
}
