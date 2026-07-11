package com.bujirun.bujirun.domain.collection.controller;

import com.bujirun.bujirun.domain.collection.dto.response.CollectionDetailResponse;
import com.bujirun.bujirun.domain.collection.dto.response.CollectionListResponse;
import com.bujirun.bujirun.domain.collection.service.CollectionService;
import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "찜(컬렉션)", description = "찜한 여행지 목록 및 상세 조회, 찜 취소 API")
@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @Operation(summary = "찜 목록 조회", description = "로그인한 사용자가 찜한 여행지 목록을 조회합니다.")
    @GetMapping
    public ResponseEntity<List<CollectionListResponse>> getBoard(
            @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(collectionService.getCollectionBoard(userId));
    }

    @Operation(summary = "찜 상세 조회", description = "찜한 특정 여행지의 상세 정보를 조회합니다.")
    @GetMapping("/{spotId}")
    public ResponseEntity<CollectionDetailResponse> getDetail(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spotId) {
        return ResponseEntity.ok(collectionService.getDetail(userId, spotId));
    }

    @Operation(summary = "찜 취소", description = "여행지에 대한 찜을 취소합니다.")
    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> cancel(
            @AuthenticationPrincipal UUID userId,
            @PathVariable UUID spotId) {
        collectionService.cancel(userId, spotId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "도감 스와이프 덱 조회", description = "도감 카테고리(바다/자연/문화/체험)별로 랜덤 관광지 총 10곳을 반환합니다.")
    @GetMapping("/swipe-deck")
    public ResponseEntity<List<SpotSearchResponse>> getSwipeDeck(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(collectionService.getRandomSwipeDeck(userId));
    }
}