package com.bujirun.bujirun.domain.itinerary.vote.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.LocalTime;
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

    // AI 생성을 실제로 수행한 요청의 startTime/endTime. 방장이 입력한 값과 팀원 화면에
    // 표시되는 값이 달라지는 문제(2026-09-05 발견)를 막기 위해, 프론트 URL 파라미터가 아니라
    // 이 컬럼을 시작/종료 시각의 단일 source of truth로 삼는다.
    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

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
    public void completeGeneration(String plansJson, LocalTime startTime, LocalTime endTime) {
        this.plansJson = plansJson;
        this.startTime = startTime;
        this.endTime = endTime;
        this.status = "voting";
    }
}