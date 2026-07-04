package com.bujirun.bujirun.domain.upload.service;

import com.bujirun.bujirun.domain.upload.dto.response.PresignUploadResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Duration UPLOAD_URL_DURATION = Duration.ofMinutes(10);

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    public PresignUploadResponse createPresignedUploadUrl(UUID userId, String contentType) {
        String key = "uploads/%s/%s%s".formatted(userId, UUID.randomUUID(), extensionOf(contentType));

        PutObjectRequest objectRequest = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .signatureDuration(UPLOAD_URL_DURATION)
                        .putObjectRequest(objectRequest)
                        .build());

        String publicUrl = "https://%s.s3.%s.amazonaws.com/%s".formatted(bucket, region, key);
        return new PresignUploadResponse(presigned.url().toString(), publicUrl);
    }

    private String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> "";
        };
    }
}
