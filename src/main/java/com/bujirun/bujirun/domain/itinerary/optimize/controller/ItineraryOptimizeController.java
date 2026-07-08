package com.bujirun.bujirun.domain.itinerary.optimize.controller;

import com.bujirun.bujirun.domain.itinerary.optimize.dto.request.ItineraryOptimizeRequest;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.response.ItineraryOptimizeResponse;
import com.bujirun.bujirun.domain.itinerary.optimize.service.ItineraryOptimizeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/itineraries/days")
@RequiredArgsConstructor
@Tag(name = "Itinerary Optimize", description = "일정 재최적화 API")
public class ItineraryOptimizeController {

    private final ItineraryOptimizeService itineraryOptimizeService;

    @PatchMapping("/{dayId}/optimize")
    @Operation(summary = "일정 재최적화", description = "좌표 기반 동선 재정렬 + 운영시간 반영")
    public ItineraryOptimizeResponse optimize(
            @PathVariable UUID dayId,
            @RequestBody ItineraryOptimizeRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return itineraryOptimizeService.optimizeDay(dayId, request, userId);
    }
}
