package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

// day의 항목 전체를 한 번의 원자적 요청으로 교체한다(삭제 N번 + 추가 N번을 따로 보내면
// 실시간 협업 편집에서 여러 클라이언트가 동시에 같은 변경을 쏠 때 일부만 반영되고 나머지가
// 유실될 수 있다). operationId로 같은 논리적 편집의
// 중복 요청을 감지해 두 번째 이후 요청은 재처리하지 않고 첫 요청의 결과를 그대로 돌려준다.
public record ReplaceDayItemsRequest(
        @NotNull UUID operationId,
        // 클라이언트가 마지막으로 읽은 day의 version. null이면 버전 체크를 건너뛴다(이 필드가
        // 없던 구버전 프론트와의 호환용) — 값이 있는데 서버의 현재 version과 다르면 그 사이
        // 다른 요청이 먼저 반영된 것이므로 409로 거부한다(ItineraryService.replaceDayItems 참고).
        Long expectedVersion,
        @Valid List<ItemInput> items
) {
    public record ItemInput(
            // 새로 추가되는 항목이면 null. 기존 항목을 그대로 유지하는 거면 그 항목의 기존
            // itineraryItem id를 실어 보낸다 — durationMin/travelMode/memo를 프론트가 안 보내도
            // (Yjs엔 이 필드들이 애초에 없음) 서버가 기존 값을 그대로 이어받기 위한 매칭 키.
            UUID existingItemId,
            @NotNull UUID spotId,
            LocalTime arrivalTime,
            Integer durationMin,
            @Pattern(regexp = "walk|transit|taxi|bus|subway|combo",
                    message = "travelMode은 walk, transit, taxi, bus, subway, combo 중 하나여야 합니다.")
            String travelMode,
            Integer travelTimeMin,
            String memo
    ) {}
}
