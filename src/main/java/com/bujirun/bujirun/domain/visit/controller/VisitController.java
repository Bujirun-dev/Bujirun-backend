package com.bujirun.bujirun.domain.visit.controller;

import com.bujirun.bujirun.domain.visit.dto.request.VisitRequest;
import com.bujirun.bujirun.domain.visit.dto.response.VisitResponse;
import com.bujirun.bujirun.domain.visit.service.VisitService;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.UUID;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
public class VisitController {

    private final VisitService visitService;

    @PostMapping
    public Mono<ResponseEntity<ApiResponse<VisitResponse>>> verify(
            @RequestBody @Valid VisitRequest req,
            @AuthenticationPrincipal UUID userId) {
        return Mono.fromCallable(() -> visitService.verify(req, userId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(r -> ResponseEntity.status(201).body(ApiResponse.ok(r)));
    }
}
