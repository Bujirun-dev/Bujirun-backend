package com.bujirun.bujirun.domain.itinerary.controller;

import com.bujirun.bujirun.domain.itinerary.dto.request.InternalReplaceDayItemsRequest;
import com.bujirun.bujirun.domain.itinerary.dto.response.ItineraryDayResponse;
import com.bujirun.bujirun.domain.itinerary.service.ItineraryService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;
import java.util.concurrent.Callable;

// 내부 전용(server-to-server) API. /api/internal/**은 사람의 JWT가 아니라
// X-Internal-Secret 헤더로 인증한다(SecurityConfig의 별도 SecurityFilterChain 참고) —
// node-yjs 서버가 room 단위 flush를 대신 호출하는 용도로 도입됨(3단계, 2026-09-17).
@Tag(name = "내부 전용", description = "node-yjs 등 내부 서버가 호출하는 API. 사람이 직접 호출하지 않습니다.")
@RestController
@RequestMapping("/api/internal/itineraries")
@RequiredArgsConstructor
public class InternalItineraryController {

    private final ItineraryService itineraryService;

    @Operation(summary = "[내부] 일차 항목 전체 교체(원자적)", description = """
            /api/itineraries/{itineraryId}/days/{dayId}/items(replaceDayItems)와 로직은 동일하고
            actorUserId(이 변경을 유발한 사용자)만 추가로 받는다. node-yjs 서버가 room의 flush를
            대신 호출할 때 쓴다. actorUserId가 해당 일정의 소유자/collaborator가 아니면 403.
            """)
    @PutMapping("/{itineraryId}/days/{dayId}/items")
    public Mono<ResponseEntity<ApiResponse<ItineraryDayResponse>>> replaceDayItems(
            @PathVariable UUID itineraryId,
            @PathVariable UUID dayId,
            @RequestBody @Valid InternalReplaceDayItemsRequest req) {
        return blocking(() -> itineraryService.replaceDayItemsInternal(itineraryId, dayId, req))
                .map(r -> ResponseEntity.ok(ApiResponse.ok(r)));
    }

    private <T> Mono<T> blocking(Callable<T> callable) {
        return Mono.fromCallable(callable).subscribeOn(Schedulers.boundedElastic());
    }
}
