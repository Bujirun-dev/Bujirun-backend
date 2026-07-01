package com.bujirun.bujirun.domain.collection.controller;

import com.bujirun.bujirun.domain.collection.dto.response.CollectionDetailResponse;
import com.bujirun.bujirun.domain.collection.dto.response.CollectionListResponse;
import com.bujirun.bujirun.domain.collection.service.CollectionService;
import com.bujirun.bujirun.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/collections")
@RequiredArgsConstructor
public class CollectionController {

    private final CollectionService collectionService;

    @GetMapping
    public ResponseEntity<List<CollectionListResponse>> getBoard(@CurrentUser UUID userId) {
        return ResponseEntity.ok(collectionService.getCollectionBoard(userId));
    }

    @GetMapping("/{spotId}")
    public ResponseEntity<CollectionDetailResponse> getDetail(
            @CurrentUser UUID userId, @PathVariable UUID spotId) {
        return ResponseEntity.ok(collectionService.getDetail(userId, spotId));
    }

    @DeleteMapping("/{spotId}")
    public ResponseEntity<Void> cancel(@CurrentUser UUID userId, @PathVariable UUID spotId) {
        collectionService.cancel(userId, spotId);
        return ResponseEntity.noContent().build();
    }
}