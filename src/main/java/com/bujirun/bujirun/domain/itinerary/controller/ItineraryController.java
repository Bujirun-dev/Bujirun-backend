package com.bujirun.bujirun.domain.itinerary.controller;

import com.bujirun.bujirun.domain.itinerary.dto.request.*;
import com.bujirun.bujirun.domain.itinerary.dto.response.*;
import com.bujirun.bujirun.domain.itinerary.service.ItineraryService;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    // ── Itinerary ──────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> create(
            @RequestBody @Valid CreateItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.create(req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.getById(id, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<ItinerarySummaryResponse>>>> getList(
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.getByUserId(userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.update(id, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> itineraryService.delete(id, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── Day ────────────────────────────────────────────────────────

    @PostMapping("/{itineraryId}/days")
    public Mono<ResponseEntity<ApiResponse<ItineraryDayResponse>>> addDay(
            @PathVariable UUID itineraryId,
            @RequestBody @Valid AddDayRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.addDay(itineraryId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @DeleteMapping("/{itineraryId}/days/{dayId}")
    public Mono<ResponseEntity<Void>> deleteDay(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> itineraryService.deleteDay(itineraryId, dayId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── Item ────────────────────────────────────────────────────────

    @PostMapping("/{itineraryId}/days/{dayId}/items")
    public Mono<ResponseEntity<ApiResponse<ItineraryItemResponse>>> addItem(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @RequestBody @Valid AddItemRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.addItem(itineraryId, dayId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @PatchMapping("/{itineraryId}/days/{dayId}/items/{itemId}")
    public Mono<ResponseEntity<ApiResponse<ItineraryItemResponse>>> updateItem(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateItemRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.updateItem(itineraryId, dayId, itemId, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @DeleteMapping("/{itineraryId}/days/{dayId}/items/{itemId}")
    public Mono<ResponseEntity<Void>> deleteItem(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> itineraryService.deleteItem(itineraryId, dayId, itemId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
