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

    // "generating"(AI 생성 중, 자리 선점용) 단계에서는 아직 결과가 없어 null일 수 있고,
    // completeGeneration() 호출 시점에 채워진다.
    @Column(name = "plans_json", columnDefinition = "TEXT")
    private String plansJson;

    // "generating" -> "voting" -> "confirmed" 순서로만 전이된다. 기본값을 두지 않고
    // 생성 시점(ItineraryVoteService.tryReserveGeneration)에 항상 "generating"으로 명시한다.
    @Column(nullable = false, length = 20)
    private String status;

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

    // "generating" 자리를 선점한 요청이 AI 생성을 마쳤을 때 결과를 채워 "voting"으로 전이한다.
    public void completeGeneration(String plansJson) {
        this.plansJson = plansJson;
        this.status = "voting";
    }
}