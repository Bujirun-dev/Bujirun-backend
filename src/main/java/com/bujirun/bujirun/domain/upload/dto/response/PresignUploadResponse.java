package com.bujirun.bujirun.domain.upload.dto.response;

import java.time.Instant;

// uploadUrl: 클라이언트가 이 URL로 이미지 원본을 직접 PUT (S3로 바로 전송, 서버는 바이트를 안 거침)
// publicUrl: 업로드 완료 후 photoUrl/profileImageUrl 등 필드에 저장해서 쓰는 실제 접근 URL
// expiresAt: uploadUrl(presigned URL)이 만료되는 시각 — 이 시각 이후엔 PUT이 403으로 거부됨
public record PresignUploadResponse(
        String uploadUrl,
        String publicUrl,
        Instant expiresAt
) {}
