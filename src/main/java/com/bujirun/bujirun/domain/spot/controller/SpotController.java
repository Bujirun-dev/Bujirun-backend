package com.bujirun.bujirun.domain.spot.controller;

import com.bujirun.bujirun.domain.spot.dto.response.SpotDetailResponse;
import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import com.bujirun.bujirun.domain.spot.service.SpotService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "관광지", description = "관광지(여행 스팟) 검색 API")
@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotController {

    private final SpotService spotService;

    @Operation(summary = "관광지 검색", description = "키워드, 지역(시군구), 카테고리, 정렬 기준으로 관광지를 검색합니다.")
    @GetMapping("/search")
    public ResponseEntity<List<SpotSearchResponse>> search(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer sigunguId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "RECOMMEND") String sort) {
        return ResponseEntity.ok(spotService.search(userId, keyword, sigunguId, category, sort));
    }

    @Operation(summary = "관광지 상세 조회", description = "spotId로 관광지 상세 정보(DB 기본정보 + TourAPI 개요/이미지)를 조회합니다.")
    @GetMapping("/{spotId}")
    public ResponseEntity<SpotDetailResponse> getDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spotId) {
        return ResponseEntity.ok(spotService.getDetail(userId, spotId));
    }
}