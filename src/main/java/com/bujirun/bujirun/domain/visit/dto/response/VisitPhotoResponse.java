package com.bujirun.bujirun.domain.visit.dto.response;

import com.bujirun.bujirun.domain.visit.entity.VisitPhoto;

import java.time.LocalDateTime;
import java.util.UUID;

public record VisitPhotoResponse(
        UUID photoId,
        UUID visitId,
        String photoUrl,
        LocalDateTime createdAt
) {
    public static VisitPhotoResponse from(VisitPhoto photo) {
        return new VisitPhotoResponse(
                photo.getId(),
                photo.getVisit().getId(),
                photo.getPhotoUrl(),
                photo.getCreatedAt()
        );
    }
}
