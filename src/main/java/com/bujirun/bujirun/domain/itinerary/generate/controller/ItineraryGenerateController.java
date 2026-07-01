package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.dto.request.SwipeRequest;
import com.bujirun.bujirun.domain.itinerary.generate.service.ItineraryGenerateService;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryGenerateController {

    private final ItineraryGenerateService itineraryGenerateService;

    /**
     * 스와이프 결과 기반 A/B/C 일정 자동 생성
     * POST /api/itineraries/generate
     */
    @PostMapping("/generate")
    public ApiResponse<ItineraryGenerateResponse> generateItinerary(
            @Valid @RequestBody SwipeRequest request,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(itineraryGenerateService.generateItinerary(request, userId));
    }
}