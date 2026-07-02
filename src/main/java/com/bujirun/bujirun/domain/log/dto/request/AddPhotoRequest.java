package com.bujirun.bujirun.domain.log.dto.request;

import jakarta.validation.constraints.NotBlank;

public record AddPhotoRequest(
        @NotBlank String photoUrl
) {}
