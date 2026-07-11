package com.bujirun.bujirun.domain.visit.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AttachVisitPhotoRequest(
        @NotBlank String photoUrl
) {}
