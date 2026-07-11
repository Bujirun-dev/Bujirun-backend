package com.bujirun.bujirun.domain.log.dto.request;

public record UpdateLogRequest(
        Boolean isPublic,
        Integer mood,
        String theme
) {}
