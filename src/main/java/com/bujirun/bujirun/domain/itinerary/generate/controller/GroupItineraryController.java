package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.group.dto.response.GroupPreferenceSummary; // 추가
import com.bujirun.bujirun.domain.group.service.GroupPreferenceService; // 추가
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.GroupItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.GroupItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.service.GroupItineraryGenerateService;
import com.bujirun.bujirun.domain.itinerary.vote.service.ItineraryVoteService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Optional;
import java.util.UUID;

@Tag(name = "그룹 일정 자동생성", description = "그룹 멤버들의 스와이프 결과를 취합한 그룹 일정 자동 생성 API")
@RestController
@RequestMapping("/api/itineraries/group")
@RequiredArgsConstructor
public class GroupItineraryController {

    private final GroupItineraryGenerateService groupItineraryGenerateService;
    private final ItineraryVoteService itineraryVoteService;
    private final GroupPreferenceService groupPreferenceService; // 추가

    @Operation(summary = "그룹 일정 자동 생성", description = "그룹 멤버들의 스와이프 결과를 종합하여 그룹 일정을 자동 생성합니다.")
    @PostMapping("/{groupId}/generate")
    public Mono<ResponseEntity<ApiResponse<GroupItineraryGenerateResponse>>> generate(
            @PathVariable UUID groupId,
            @RequestBody @Valid GroupItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> resolveVoteSession(groupId, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    // 그룹 멤버 여러 명이 거의 동시에 투표를 시작하면, AI 호출(최대 60초) 전에
    // "생성 중" 자리를 먼저 선점 시도한다. DB 유니크 인덱스(V36)가 그룹당 하나만
    // 통과시키므로, 선점에 이긴 요청만 실제로 OpenAI를 호출하고 나머지는 그 결과가
    // 나올 때까지 기다렸다가 그대로 합류한다 — 그룹원마다 관광지 조합이 달라지던
    // 문제(2026-09-02 발견)는 AI 호출 자체가 그룹당 한 번만 일어나야 막을 수 있다.
    private GroupItineraryGenerateResponse resolveVoteSession(UUID groupId, GroupItineraryRequest req, UUID userId) {
        // 추가: 그룹원 취향 집계는 매 요청마다 최신 상태로 계산해 응답에 함께 내려준다 (AI 호출 없는 순수 집계)
        GroupPreferenceSummary groupSummary = groupPreferenceService.summarize(groupId);

        Optional<GroupItineraryGenerateResponse> alreadyDone = itineraryVoteService.findActiveSession(groupId);
        if (alreadyDone.isPresent()) {
            return alreadyDone.get().toBuilder().groupSummary(groupSummary).build();
        }

        Optional<UUID> reserved = itineraryVoteService.tryReserveGeneration(groupId);
        if (reserved.isPresent()) {
            UUID sessionId = reserved.get();
            try {
                ItineraryGenerateResponse generated =
                        groupItineraryGenerateService.generateGroupItinerary(groupId, req, userId, groupSummary); // 추가: groupSummary 전달
                // 실제로 AI 생성에 쓰인 startTime/endTime을 세션에 함께 저장한다 — 이후 이
                // 세션을 조회하는 모든 멤버가 각자의 화면 상태가 아니라 이 값을 보게 하기 위함.
                itineraryVoteService.completeGeneration(sessionId, generated, req.getStartTime(), req.getEndTime());
                return GroupItineraryGenerateResponse.builder()
                        .voteSessionId(sessionId)
                        .plans(generated)
                        .groupSummary(groupSummary) // 추가
                        .startTime(req.getStartTime())
                        .endTime(req.getEndTime())
                        .build();
            } catch (RuntimeException e) {
                // 생성 실패 시 선점한 자리를 비워서 다음 요청이 처음부터 다시 시도할 수 있게 한다
                itineraryVoteService.abandonGeneration(sessionId);
                throw e;
            }
        }

        // 선점 실패 → 다른 멤버가 이미 생성 중이거나 막 완료함. 그 결과가 나올 때까지 대기 후 합류.
        return itineraryVoteService.waitForActiveSession(groupId)
                .map(existing -> existing.toBuilder().groupSummary(groupSummary).build())
                .orElseGet(() -> resolveVoteSession(groupId, req, userId)); // 선점한 쪽이 실패해서 자리가 비었으면 처음부터 재시도
    }


    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}