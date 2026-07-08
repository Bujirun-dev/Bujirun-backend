package com.bujirun.bujirun.domain.itinerary.optimize.controller;

import com.bujirun.bujirun.domain.itinerary.optimize.dto.request.ItineraryOptimizeRequest;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.response.ItineraryOptimizeResponse;
import com.bujirun.bujirun.domain.itinerary.optimize.service.ItineraryOptimizeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/itineraries/days")
@RequiredArgsConstructor
public class ItineraryOptimizeController {

    private final ItineraryOptimizeService itineraryOptimizeService;

    @PatchMapping("/{dayId}/optimize")
    public ItineraryOptimizeResponse optimize(
            @PathVariable UUID dayId,
            @RequestBody ItineraryOptimizeRequest request,
            @AuthenticationPrincipal UUID userId
    ) {
        return itineraryOptimizeService.optimizeDay(dayId, request, userId);
    }
}
