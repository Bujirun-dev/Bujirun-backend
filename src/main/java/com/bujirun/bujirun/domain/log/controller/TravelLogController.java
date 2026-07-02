package com.bujirun.bujirun.domain.log.controller;

import com.bujirun.bujirun.domain.log.dto.request.AddHashtagRequest;
import com.bujirun.bujirun.domain.log.dto.request.AddPhotoRequest;
import com.bujirun.bujirun.domain.log.dto.request.CreateLogRequest;
import com.bujirun.bujirun.domain.log.dto.request.UpdateLogRequest;
import com.bujirun.bujirun.domain.log.dto.response.*;
import com.bujirun.bujirun.domain.log.service.TravelLogService;
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
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class TravelLogController {

    private final TravelLogService travelLogService;

    // ── 로그 CRUD ──────────────────────────────────────────────────

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<TravelLogDetailResponse>>> create(
            @RequestBody @Valid CreateLogRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.create(req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<TravelLogDetailResponse>>> getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.getDetail(id, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<ApiResponse<List<TravelLogSummaryResponse>>>> getMyLogs(
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.getMyLogs(userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @GetMapping("/public")
    public Mono<ResponseEntity<ApiResponse<List<TravelLogSummaryResponse>>>> getPublicLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "latest") String sort) {
        return blocking(() -> travelLogService.getPublicLogs(category, sort))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @GetMapping("/spot/{spotId}")
    public Mono<ResponseEntity<ApiResponse<List<TravelLogSummaryResponse>>>> getLogsBySpot(
            @PathVariable UUID spotId) {
        return blocking(() -> travelLogService.getLogsBySpotId(spotId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @PatchMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<TravelLogDetailResponse>>> update(
            @PathVariable UUID id,
            @RequestBody UpdateLogRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.update(id, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> travelLogService.delete(id, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── 사진 ────────────────────────────────────────────────────────

    @PostMapping("/{logId}/items/{itemId}/photos")
    public Mono<ResponseEntity<ApiResponse<TravelLogPhotoResponse>>> addPhoto(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @RequestBody @Valid AddPhotoRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.addPhoto(logId, itemId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @DeleteMapping("/{logId}/items/{itemId}/photos/{photoId}")
    public Mono<ResponseEntity<Void>> deletePhoto(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> travelLogService.deletePhoto(logId, itemId, photoId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @PatchMapping("/{logId}/items/{itemId}/photos/{photoId}/representative")
    public Mono<ResponseEntity<ApiResponse<TravelLogPhotoResponse>>> setRepresentative(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @PathVariable UUID photoId,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.setRepresentativePhoto(logId, itemId, photoId, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    // ── 해시태그 ────────────────────────────────────────────────────

    @PostMapping("/{logId}/items/{itemId}/hashtags")
    public Mono<ResponseEntity<ApiResponse<TravelLogHashtagResponse>>> addHashtag(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @RequestBody @Valid AddHashtagRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.addHashtag(logId, itemId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @DeleteMapping("/{logId}/items/{itemId}/hashtags/{hashtagId}")
    public Mono<ResponseEntity<Void>> deleteHashtag(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @PathVariable UUID hashtagId,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> travelLogService.deleteHashtag(logId, itemId, hashtagId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────

    private <T> Mono<T> blocking(java.util.concurrent.Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
