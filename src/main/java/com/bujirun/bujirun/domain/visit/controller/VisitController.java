package com.bujirun.bujirun.domain.visit.controller;

import com.bujirun.bujirun.domain.visit.dto.request.AttachVisitPhotoRequest;
import com.bujirun.bujirun.domain.visit.dto.request.VisitRequest;
import com.bujirun.bujirun.domain.visit.dto.response.VisitHistoryResponse;
import com.bujirun.bujirun.domain.visit.dto.response.VisitPhotoResponse;
import com.bujirun.bujirun.domain.visit.dto.response.VisitResponse;
import com.bujirun.bujirun.domain.visit.service.VisitService;
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

import java.util.List;
import java.util.UUID;

@Tag(name = "방문 인증", description = "여행지 방문 인증 API")
@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @Operation(summary = "방문 인증", description = "위치 정보를 기반으로 사용자가 해당 장소를 실제 방문했는지 인증합니다. " +
            "itineraryItemId를 함께 보내면 이 인증이 어느 일정의 어느 방문 항목에 대한 것인지 연결됩니다(선택) — " +
            "같은 관광지를 여러 일정에서 각각 인증한 경우 특정 일정의 인증만 구분해서 조회하고 싶을 때 사용하세요.")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<VisitResponse>>> verify(
            @RequestBody @Valid VisitRequest req,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromCallable(() -> visitService.verify(req, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "방문 인증 사진 첨부", description = "GPS 인증에 성공한 방문 기록에 촬영한 사진을 첨부합니다. photoUrl은 /api/uploads/presign으로 S3에 업로드 후 받은 publicUrl을 그대로 사용합니다.")
    @PostMapping("/{visitId}/photos")
    public Mono<ResponseEntity<ApiResponse<VisitPhotoResponse>>> attachPhoto(
            @PathVariable UUID visitId,
            @RequestBody @Valid AttachVisitPhotoRequest req,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromCallable(() -> visitService.attachPhoto(visitId, req, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "방문 인증 이력 조회", description = "로그인한 사용자의 방문 인증 시도 이력을 최신순으로 조회합니다.")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<VisitHistoryResponse>>>> getHistory(
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromCallable(() -> visitService.getHistory(userId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }
}
