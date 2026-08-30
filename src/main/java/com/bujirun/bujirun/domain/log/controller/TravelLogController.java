package com.bujirun.bujirun.domain.log.controller;

import com.bujirun.bujirun.domain.itinerary.dto.response.ItineraryDetailResponse;
import com.bujirun.bujirun.domain.log.dto.request.AddHashtagRequest;
import com.bujirun.bujirun.domain.log.dto.request.AddPhotoRequest;
import com.bujirun.bujirun.domain.log.dto.request.CopyLogRequest;
import com.bujirun.bujirun.domain.log.dto.request.CreateLogRequest;
import com.bujirun.bujirun.domain.log.dto.request.UpdateLogRequest;
import com.bujirun.bujirun.domain.log.dto.response.*;
import com.bujirun.bujirun.domain.log.service.TravelLogService;
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

@Tag(name = "여행 기록", description = "여행 기록(로그) 및 사진, 해시태그 관리 API")
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class TravelLogController {

    private final TravelLogService travelLogService;

    // ── 로그 CRUD ──────────────────────────────────────────────────

    @Operation(summary = "여행 기록 생성", description = "새로운 여행 기록(로그)을 생성합니다.")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<TravelLogDetailResponse>>> create(
            @RequestBody @Valid CreateLogRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.create(req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "여행 기록 상세 조회", description = "여행 기록 ID로 사진, 해시태그를 포함한 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<TravelLogDetailResponse>>> getDetail(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.getDetail(id, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "내 여행 기록 목록 조회", description = "로그인한 사용자가 작성한 여행 기록 목록을 조회합니다.")
    @GetMapping("/me")
    public Mono<ResponseEntity<ApiResponse<List<TravelLogSummaryResponse>>>> getMyLogs(
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.getMyLogs(userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "일정별 여행 기록 존재 여부 확인", description = """
            주어진 itineraryId 목록에 대해 로그인한 사용자가 이미 여행 기록(영수증)을 작성했는지 배치로 확인합니다.
            아직 기록이 없는 일정(예: 다음 날 일정)을 찾아 영수증 발행 화면을 노출하는 용도로 사용합니다.

            이미 종료된(endAt이 오늘 이전) 일정 중 로그가 없는 것은 이 호출 시점에 기본값(비공개, mood/theme 없음)으로
            자동 생성됩니다 — 사용자가 영수증 발행 팝업에서 실제로 "발행"을 누르지 않아도 방문 인증 사진 등
            데이터가 유실되지 않도록 하기 위함(2026-08-30 결정). 그 결과 hasLog는 이후 계속 true가 되며,
            프론트는 반환된 logId로 PATCH /api/logs/{id}를 호출해 mood/theme/공개여부만 채우면 됩니다
            (이미 자동 생성됐기 때문에 POST /api/logs는 다시 호출할 수 없습니다).

            영수증 팝업을 다시 띄울지는 hasLog가 아니라 receiptCompleted(mood까지 채워 실제로 발행을
            마쳤는지)로 판단해야 합니다 — hasLog는 자동 생성 직후부터 항상 true이기 때문입니다.
            """)
    @GetMapping("/exists")
    public Mono<ResponseEntity<ApiResponse<List<LogExistenceResponse>>>> checkLogExists(
            @RequestParam List<UUID> itineraryIds,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.checkLogExists(itineraryIds, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "영수증 발행 팝업 '다시 묻지 않음'", description = """
            완료된 일정의 영수증 발행(여행 기록 작성) 유도 팝업에서 '다시 묻지 않음'을 선택했을 때 호출합니다.
            이후 GET /api/logs/exists 응답에서 해당 일정의 promptDismissed가 true가 되어 프론트가 팝업을 띄우지 않습니다.
            일정 소유자 또는 그룹원만 호출할 수 있고, 같은 일정에 여러 번 호출해도 안전합니다(idempotent).
            """)
    @PostMapping("/receipt-prompt/{itineraryId}/dismiss")
    public Mono<ResponseEntity<Void>> dismissReceiptPrompt(
            @PathVariable UUID itineraryId,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> travelLogService.dismissReceiptPrompt(itineraryId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @Operation(summary = "영수증 발행 팝업 '다시 묻지 않음' 해제", description = """
            '다시 묻지 않음'을 취소해 다시 영수증 발행 팝업을 받도록 되돌립니다. 기록이 없으면 아무 일도 하지 않습니다.
            """)
    @DeleteMapping("/receipt-prompt/{itineraryId}/dismiss")
    public Mono<ResponseEntity<Void>> restoreReceiptPrompt(
            @PathVariable UUID itineraryId,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> travelLogService.restoreReceiptPrompt(itineraryId, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @Operation(summary = "공개 여행 기록 목록 조회", description = "다른 사용자들에게 공개된 여행 기록을 카테고리, 정렬 기준으로 조회합니다.")
    @GetMapping("/public")
    public Mono<ResponseEntity<ApiResponse<List<TravelLogSummaryResponse>>>> getPublicLogs(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "latest") String sort) {
        return blocking(() -> travelLogService.getPublicLogs(category, sort))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "여행지별 기록 조회", description = "특정 여행지(스팟)에 대한 여행 기록 목록을 조회합니다.")
    @GetMapping("/spot/{spotId}")
    public Mono<ResponseEntity<ApiResponse<List<TravelLogSummaryResponse>>>> getLogsBySpot(
            @PathVariable UUID spotId) {
        return blocking(() -> travelLogService.getLogsBySpotId(spotId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "여행 기록으로 일정 복사", description = """
            공개된(또는 본인) 여행 기록의 일정을 복제해 내 소유의 새 일정으로 생성합니다.
            groupId를 지정하면 그 그룹의 공유 일정으로 생성됩니다(요청자가 해당 그룹 멤버일 때만 허용).

            아직 그룹이 없어서 친구들을 새로 초대하고 싶다면:
            1) POST /api/groups 로 그룹을 먼저 생성 (생성자는 자동으로 멤버 등록됨)
            2) 그 응답의 groupId를 이 API의 groupId로 그대로 전달
            → 두 호출을 이어붙이면 "새 그룹 생성 후 그 그룹으로 바로 복사" 흐름이 됩니다. 별도의 결합 API는 없습니다.
            """)
    @PostMapping("/{id}/copy")
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> copyToItinerary(
            @PathVariable UUID id,
            @RequestBody(required = false) CopyLogRequest req,
            @AuthenticationPrincipal UUID userId) {
        UUID groupId = req != null ? req.groupId() : null;
        return blocking(() -> travelLogService.copyToItinerary(id, userId, groupId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "여행 기록 수정", description = "여행 기록의 내용을 수정합니다.")
    @PatchMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<TravelLogDetailResponse>>> update(
            @PathVariable UUID id,
            @RequestBody UpdateLogRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.update(id, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "여행 기록 삭제", description = "여행 기록을 삭제합니다.")
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> travelLogService.delete(id, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── 사진 ────────────────────────────────────────────────────────

    @Operation(summary = "사진 추가", description = "여행 기록의 특정 방문 항목에 사진을 추가합니다.")
    @PostMapping("/{logId}/items/{itemId}/photos")
    public Mono<ResponseEntity<ApiResponse<TravelLogPhotoResponse>>> addPhoto(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @RequestBody @Valid AddPhotoRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.addPhoto(logId, itemId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "사진 삭제", description = "여행 기록의 방문 항목에서 특정 사진을 삭제합니다.")
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

    @Operation(summary = "대표 사진 설정", description = "방문 항목에 포함된 사진 중 하나를 대표 사진으로 지정합니다.")
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

    @Operation(summary = "해시태그 추가", description = "여행 기록의 방문 항목에 해시태그를 추가합니다.")
    @PostMapping("/{logId}/items/{itemId}/hashtags")
    public Mono<ResponseEntity<ApiResponse<TravelLogHashtagResponse>>> addHashtag(
            @PathVariable UUID logId,
            @PathVariable UUID itemId,
            @RequestBody @Valid AddHashtagRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> travelLogService.addHashtag(logId, itemId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "해시태그 삭제", description = "여행 기록의 방문 항목에서 특정 해시태그를 삭제합니다.")
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
