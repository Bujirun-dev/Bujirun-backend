package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

// 일차(day)에 속한 방문 항목 전체의 최종 순서를 한 번에 원자적으로 반영하기 위한 요청.
// itemIds는 그 day에 현재 존재하는 항목 id 전체를 원하는 순서대로 담아야 한다(부분 목록 불가) —
// 항목별 PATCH로 순서를 나눠 반영하면 동시편집 중 서로 다른 클라이언트의 갱신이 뒤섞여
// order_index가 충돌할 수 있어서(2026-08-12 프로덕션에서 실제 발견) 이 API로 대체한다.
public record ReorderItemsRequest(
        @NotEmpty List<UUID> itemIds
) {}
