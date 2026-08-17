package com.bujirun.bujirun.domain.itinerary.controller;

import com.bujirun.bujirun.domain.itinerary.dto.request.*;
import com.bujirun.bujirun.domain.itinerary.dto.response.*;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.domain.itinerary.service.ItineraryService;
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

@Tag(name = "일정", description = "여행 일정(이티너러리) 및 일차/방문 항목 관리 API")
@RestController
@RequestMapping("/api/itineraries")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryService itineraryService;

    // ── Itinerary ──────────────────────────────────────────────────

    @Operation(summary = "일정 생성", description = "새로운 여행 일정을 생성합니다.")
    @PostMapping
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> create(
            @RequestBody @Valid CreateItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.create(req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "일정 상세 조회", description = "일정 ID로 일차 및 방문 항목을 포함한 일정 상세 정보를 조회합니다.")
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> getById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.getById(id, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "내 일정 목록 조회", description = "로그인한 사용자가 만든 일정 목록을 요약 정보로 조회합니다.")
    @GetMapping
    public Mono<ResponseEntity<ApiResponse<List<ItinerarySummaryResponse>>>> getList(
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.getByUserId(userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "일정 수정", description = "일정의 제목, 기간 등 기본 정보를 수정합니다.")
    @PatchMapping("/{id}")
    public Mono<ResponseEntity<ApiResponse<ItineraryDetailResponse>>> update(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateItineraryRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.update(id, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "일정 삭제", description = "개인 일정을 삭제합니다. 그룹 일정은 대신 나가기(leave)를 사용해야 합니다.")
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> itineraryService.delete(id, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @Operation(summary = "일정 나가기", description = "그룹 일정에서 나갑니다. 마지막 멤버가 나가면 그룹과 일정이 함께 삭제됩니다.")
    @PostMapping("/{id}/leave")
    public Mono<ResponseEntity<Void>> leave(
            @PathVariable UUID id,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> itineraryService.leave(id, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    // ── Day ────────────────────────────────────────────────────────

    @Operation(summary = "일차 추가", description = "일정에 새로운 여행 일차(Day)를 추가합니다.")
    @PostMapping("/{itineraryId}/days")
    public Mono<ResponseEntity<ApiResponse<ItineraryDayResponse>>> addDay(
            @PathVariable UUID itineraryId,
            @RequestBody @Valid AddDayRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.addDay(itineraryId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "일차 삭제", description = "일정에서 특정 일차(Day)를 삭제합니다.")
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

    @Operation(summary = "방문 항목 추가", description = "특정 일차에 방문할 장소(항목)를 추가합니다.")
    @PostMapping("/{itineraryId}/days/{dayId}/items")
    public Mono<ResponseEntity<ApiResponse<ItineraryItemResponse>>> addItem(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @RequestBody @Valid AddItemRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.addItem(itineraryId, dayId, req, userId))
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }

    @Operation(summary = "방문 항목 수정", description = "일차에 속한 방문 항목의 시간, 순서 등 정보를 수정합니다.")
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

    @Operation(summary = "방문 항목 이동수단 변경",
            description = "사용자가 도보/대중교통/택시 중 원하는 이동수단을 선택하면, " +
                    "직전 방문 항목과의 구간을 해당 수단 기준으로 재계산해 저장합니다.")
    @PatchMapping("/{itineraryId}/days/{dayId}/items/{itemId}/travel-mode")
    public Mono<ResponseEntity<ApiResponse<ItineraryItemResponse>>> updateTravelMode(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @PathVariable UUID itemId,
            @RequestBody @Valid UpdateTravelModeRequest req,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.updateTravelMode(itineraryId, dayId, itemId, req, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    // 이동수단 변경 화면에서 확정 전 버스/지하철/택시/도보 후보의 실제 소요시간·요금을 미리 보여주기 위한 조회 API
    @Operation(summary = "이동수단 변경 후보 옵션 조회",
            description = "직전 방문 항목과의 구간에 대해 선택 가능한 이동수단 후보(버스/지하철/택시/도보)와 " +
                    "각각의 실제 소요시간·요금을 조회합니다. DB에 저장된 값이 아니라 매번 새로 계산한 값이며, " +
                    "확정하려면 이동수단 변경(travel-mode) API를 별도로 호출해야 합니다.")
    @GetMapping("/{itineraryId}/days/{dayId}/items/{itemId}/travel-mode/options")
    public Mono<ResponseEntity<ApiResponse<List<TransitOption>>>> getTravelModeOptions(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @PathVariable UUID itemId,
            @AuthenticationPrincipal UUID userId) {
        return blocking(() -> itineraryService.getTravelModeOptions(itineraryId, dayId, itemId, userId))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    @Operation(summary = "방문 항목 순서 일괄 변경",
            description = "일차에 속한 방문 항목 전체의 순서를 한 번에 원자적으로 반영합니다. " +
                    "항목별 개별 PATCH로 순서를 나눠 반영하면 동시편집 시 order_index가 충돌할 수 있어 도입됨. " +
                    "itemIds는 그 일차에 존재하는 항목 id 전체를 원하는 순서대로 담아야 합니다.")
    @PatchMapping("/{itineraryId}/days/{dayId}/items/order")
    public Mono<ResponseEntity<Void>> reorderItems(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @RequestBody @Valid ReorderItemsRequest req,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromRunnable(() -> itineraryService.reorderItems(itineraryId, dayId, req, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .thenReturn(ResponseEntity.noContent().<Void>build());
    }

    @Operation(summary = "방문 항목 삭제", description = "일차에서 특정 방문 항목을 삭제합니다.")
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
