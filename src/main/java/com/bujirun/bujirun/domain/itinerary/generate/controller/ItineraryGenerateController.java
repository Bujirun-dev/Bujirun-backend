package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.itinerary.generate.service.ItineraryGenerateService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "일정 자동생성", description = "스와이프 결과 기반 개인 일정 자동 생성 API")
@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryGenerateController {

    private final ItineraryGenerateService itineraryGenerateService;

    @Operation(summary = "일정 자동 생성", description = "사용자의 스와이프(좋아요/싫어요) 결과를 기반으로 A/B/C 3가지 일정 후보를 자동 생성합니다.")
    @PostMapping("/generate")
    public ApiResponse<ItineraryGenerateResponse> generateItinerary(
            @Valid @RequestBody SwipeRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(itineraryGenerateService.generateItinerary(request, userId));
    }
}