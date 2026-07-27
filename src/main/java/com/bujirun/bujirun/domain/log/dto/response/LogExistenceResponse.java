package com.bujirun.bujirun.domain.log.dto.response;

import java.util.UUID;

public record LogExistenceResponse(
        UUID itineraryId,
        boolean hasLog,
        UUID logId
) {}
