package com.bujirun.bujirun.domain.spot.controller;

import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import com.bujirun.bujirun.domain.spot.service.SpotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/spots")
@RequiredArgsConstructor
public class SpotController {

    private final SpotService spotService;

    @GetMapping("/search")
    public ResponseEntity<List<SpotSearchResponse>> search(
            @AuthenticationPrincipal UUID userId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer sigunguId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "RECOMMEND") String sort) {
        return ResponseEntity.ok(spotService.search(userId, keyword, sigunguId, category, sort));
    }
}