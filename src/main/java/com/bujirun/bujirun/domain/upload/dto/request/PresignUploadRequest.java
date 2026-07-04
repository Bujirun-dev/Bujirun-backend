package com.bujirun.bujirun.domain.upload.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PresignUploadRequest(
        @NotBlank
        @Pattern(regexp = "^image/(jpeg|png|webp|gif)$", message = "지원하지 않는 이미지 형식입니다.")
        String contentType
) {}
