package com.bujirun.bujirun.domain.itinerary.vote.repository;

import com.bujirun.bujirun.domain.itinerary.vote.entity.ItineraryVoteSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ItineraryVoteSessionRepository extends JpaRepository<ItineraryVoteSession, UUID> {

    Optional<ItineraryVoteSession> findFirstByGroupIdAndStatusOrderByCreatedAtDesc(UUID groupId, String status);

}