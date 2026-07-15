package com.bujirun.bujirun.domain.itinerary.vote.controller;

import com.bujirun.bujirun.domain.itinerary.vote.dto.request.CastVoteRequest;
import com.bujirun.bujirun.domain.itinerary.vote.dto.request.FinalizeItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.vote.dto.response.VoteStatusResponse;
import com.bujirun.bujirun.domain.itinerary.vote.service.ItineraryVoteService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "일정 투표", description = "그룹 일정 A/B/C안 투표 및 확정 API")
@RestController
@RequestMapping("/api/itineraries/vote-sessions")
@RequiredArgsConstructor
public class ItineraryVoteController {

    private final ItineraryVoteService itineraryVoteService;

    @Operation(summary = "투표 참여", description = "A/B/C안 중 하나에 투표합니다.")
    @PostMapping("/{sessionId}/votes")
    public ApiResponse<VoteStatusResponse> castVote(
            @PathVariable UUID sessionId,
            @Valid @RequestBody CastVoteRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(itineraryVoteService.castVote(sessionId, request, userId));
    }



    @Operation(summary = "투표 현황 조회")
    @GetMapping("/{sessionId}")
    public ApiResponse<VoteStatusResponse> getStatus(
            @PathVariable UUID sessionId,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(itineraryVoteService.getVoteStatus(sessionId, userId));
    }


    @Operation(summary = "일정 확정 (리더 전용)",
            description = "투표 결과 최다득표안을 확정합니다. 동률이면 selectedPlan을 지정해야 합니다. " +
                    "freePass=true면 투표 결과와 무관하게 selectedPlan으로 즉시 확정합니다.")
    @PostMapping("/{sessionId}/finalize")
    public ApiResponse<UUID> finalize(
            @PathVariable UUID sessionId,
            @Valid @RequestBody FinalizeItineraryRequest request,
            @AuthenticationPrincipal UUID userId) {
        request.setRequesterId(userId);
        return ApiResponse.ok(itineraryVoteService.finalizeByLeader(sessionId, request, userId));
    }

}