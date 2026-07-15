package com.bujirun.bujirun.domain.log.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateLogRequest(
        @NotNull UUID itineraryId,
        boolean isPublic,
        Integer mood,
        String theme
) {}
