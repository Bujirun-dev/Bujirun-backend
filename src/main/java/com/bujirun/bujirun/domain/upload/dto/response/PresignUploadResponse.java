package com.bujirun.bujirun.domain.upload.dto.response;

// uploadUrl: 클라이언트가 이 URL로 이미지 원본을 직접 PUT (S3로 바로 전송, 서버는 바이트를 안 거침)
// publicUrl: 업로드 완료 후 photoUrl/profileImageUrl 등 필드에 저장해서 쓰는 실제 접근 URL
public record PresignUploadResponse(
        String uploadUrl,
        String publicUrl
) {}
