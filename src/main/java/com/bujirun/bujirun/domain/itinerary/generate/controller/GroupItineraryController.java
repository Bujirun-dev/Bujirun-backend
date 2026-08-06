package com.bujirun.bujirun.domain.itinerary.generate.controller;

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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@Tag(name = "그룹 일정 자동생성", description = "그룹 멤버들의 스와이프 결과를 취합한 그룹 일정 자동 생성 API")
@RestController
@RequestMapping("/api/itineraries/group")
@RequiredArgsConstructor
public class GroupItineraryController {

    private final GroupItineraryGenerateService groupItineraryGenerateService;
    private final ItineraryVoteService itineraryVoteService;

    @Operation(summary = "그룹 일정 자동 생성", description = "그룹 멤버들의 스와이프 결과를 종합하여 그룹 일정을 자동 생성합니다.")
    @PostMapping("/{groupId}/generate")
    public Mono<ResponseEntity<ApiResponse<GroupItineraryGenerateResponse>>> generate(
            @PathVariable UUID groupId,
            @RequestBody @Valid GroupItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> resolveVoteSession(groupId, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    // 그룹 멤버 여러 명이 거의 동시에 투표를 시작하면 findActiveSession()에서
    // 서로 "아직 세션 없음"을 보고 각자 startVoteSession()을 시도할 수 있다.
    // DB 유니크 인덱스(V26)가 하나만 통과시키므로, 진 쪽은 새로 만들지 않고
    // 이긴 쪽 세션을 재조회해서 그대로 합류한다.
    private GroupItineraryGenerateResponse resolveVoteSession(UUID groupId, GroupItineraryRequest req, UUID userId) {
        return itineraryVoteService.findActiveSession(groupId)
                .orElseGet(() -> {
                    ItineraryGenerateResponse generated =
                            groupItineraryGenerateService.generateGroupItinerary(groupId, req, userId);
                    try {
                        UUID voteSessionId = itineraryVoteService.startVoteSession(groupId, generated);
                        return GroupItineraryGenerateResponse.builder()
                                .voteSessionId(voteSessionId)
                                .plans(generated)
                                .build();
                    } catch (DataIntegrityViolationException e) {
                        return itineraryVoteService.findActiveSession(groupId)
                                .orElseThrow(() -> new IllegalStateException("투표 세션 생성 경합 처리 실패", e));
                    }
                });
    }


    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}