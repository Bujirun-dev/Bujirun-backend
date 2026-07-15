package com.bujirun.bujirun.domain.itinerary.vote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "itinerary_vote_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ItineraryVoteSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "plans_json", columnDefinition = "TEXT", nullable = false)
    private String plansJson;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "voting";

    @Column(name = "confirmed_plan", length = 1)
    private String confirmedPlan;

    @Column(name = "itinerary_id")
    private UUID itineraryId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { createdAt = LocalDateTime.now(); }

    public void confirm(String plan, UUID itineraryId) {
        this.status = "confirmed";
        this.confirmedPlan = plan;
        this.itineraryId = itineraryId;
    }
}