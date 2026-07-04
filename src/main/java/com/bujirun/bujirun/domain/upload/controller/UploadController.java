package com.bujirun.bujirun.domain.upload.controller;

import com.bujirun.bujirun.domain.upload.dto.request.PresignUploadRequest;
import com.bujirun.bujirun.domain.upload.dto.response.PresignUploadResponse;
import com.bujirun.bujirun.domain.upload.service.UploadService;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    // 클라이언트가 이 URL로 이미지를 S3에 직접 PUT 업로드하고, 응답의 publicUrl을
    // photoUrl/profileImageUrl 등 기존 필드에 그대로 넣어서 사용
    @PostMapping("/presign")
    public ApiResponse<PresignUploadResponse> presign(
            @RequestBody @Valid PresignUploadRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(uploadService.createPresignedUploadUrl(userId, req.contentType()));
    }
}
