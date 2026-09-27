package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

// node-yjs 서버가 room의 flush를 대신 호출할 때 쓰는 요청. ReplaceDayItemsRequest와 필드가
// 같고 actorUserId만 추가됐다 — 사람이 직접 호출하는 경로가 아니라 이 값으로 "누가 유발한
// 변경인지"를 실어보내야 권한 체크(소유자/collaborator)와 감사 로그가 가능하다.
public record InternalReplaceDayItemsRequest(
        @NotNull UUID actorUserId,
        @NotNull UUID operationId,
        Long expectedVersion,
        @Valid List<ReplaceDayItemsRequest.ItemInput> items
) {}
