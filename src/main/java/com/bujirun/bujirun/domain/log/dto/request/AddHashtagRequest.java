package com.bujirun.bujirun.domain.log.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddHashtagRequest(
        @NotBlank @Size(max = 50) String tag
) {}
