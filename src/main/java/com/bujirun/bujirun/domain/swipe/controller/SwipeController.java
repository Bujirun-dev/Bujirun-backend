package com.bujirun.bujirun.domain.swipe.controller;

import com.bujirun.bujirun.domain.swipe.dto.request.SwipeSubmitRequest;
import com.bujirun.bujirun.domain.swipe.dto.response.SwipeSessionResponse;
import com.bujirun.bujirun.domain.swipe.dto.response.SwipeStatusResponse;
import com.bujirun.bujirun.domain.swipe.service.SwipeService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "스와이프", description = "관광지 스와이프(좋아요/싫어요) 결과 저장 API")
@RestController
@RequestMapping("/api/swipes")
@RequiredArgsConstructor
public class SwipeController {

    private final SwipeService swipeService;

    @Operation(summary = "스와이프 결과 제출",
            description = "사용자의 스와이프(좋아요/싫어요) 결과를 세션 단위로 저장합니다. " +
                    "groupId를 함께 지정하면 그룹 일정 자동 생성(/api/itineraries/group/{groupId}/generate) 시 취합 대상이 됩니다.")
    @PostMapping
    public ApiResponse<SwipeSessionResponse> submit(
            @Valid @RequestBody SwipeSubmitRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(swipeService.submitSwipeSession(request, userId));
    }

    @Operation(summary = "그룹 스와이프 완료 현황 조회",
            description = "그룹원 중 스와이프를 완료한 인원 수와 전체 인원 수를 조회합니다. " +
                    "대기 화면에서 폴링용으로 사용됩니다.")
    @GetMapping("/status")
    public ApiResponse<SwipeStatusResponse> getStatus(
            @RequestParam UUID groupId,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(swipeService.getSwipeStatus(groupId, userId));
    }
}