package com.bujirun.bujirun.domain.upload.controller;

import com.bujirun.bujirun.domain.upload.dto.request.PresignUploadRequest;
import com.bujirun.bujirun.domain.upload.dto.response.PresignUploadResponse;
import com.bujirun.bujirun.domain.upload.service.UploadService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@Tag(name = "파일 업로드", description = "S3 이미지 업로드용 presigned URL 발급 API")
@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    // 클라이언트가 이 URL로 이미지를 S3에 직접 PUT 업로드하고, 응답의 publicUrl을
    // photoUrl/profileImageUrl 등 기존 필드에 그대로 넣어서 사용
    @Operation(summary = "S3 업로드용 Presigned URL 발급", description = "클라이언트가 이미지를 S3에 직접 PUT 업로드할 수 있는 presigned URL을 발급합니다. 응답의 publicUrl을 photoUrl/profileImageUrl 등 기존 필드에 그대로 사용하면 됩니다.")
    @PostMapping("/presign")
    public ApiResponse<PresignUploadResponse> presign(
            @RequestBody @Valid PresignUploadRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(uploadService.createPresignedUploadUrl(userId, req.contentType()));
    }
}
