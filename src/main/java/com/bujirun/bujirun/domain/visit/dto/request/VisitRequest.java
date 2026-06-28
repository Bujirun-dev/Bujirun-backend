package com.bujirun.bujirun.domain.visit.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;


public record VisitRequest(
        @NotNull UUID tourSpotId,
        @NotNull Double gpsLat,
        @NotNull Double gpsLng
) {}
