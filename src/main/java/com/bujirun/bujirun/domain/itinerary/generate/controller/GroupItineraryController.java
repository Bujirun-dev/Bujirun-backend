package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.itinerary.generate.dto.request.GroupItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.ItineraryGenerateResponse;
import com.bujirun.bujirun.domain.itinerary.generate.service.GroupItineraryGenerateService;
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

import java.util.UUID;

@Tag(name = "그룹 일정 자동생성", description = "그룹 멤버들의 스와이프 결과를 취합한 그룹 일정 자동 생성 API")
@RestController
@RequestMapping("/api/itineraries/group")
@RequiredArgsConstructor
public class GroupItineraryController {

    private final GroupItineraryGenerateService groupItineraryGenerateService;

    @Operation(summary = "그룹 일정 자동 생성", description = "그룹 멤버들의 스와이프 결과를 종합하여 그룹 일정을 자동 생성합니다.")
    @PostMapping("/{groupId}/generate")
    public Mono<ResponseEntity<ApiResponse<ItineraryGenerateResponse>>> generate(
            @PathVariable UUID groupId,
            @RequestBody @Valid GroupItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> groupItineraryGenerateService.generateGroupItinerary(groupId, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}