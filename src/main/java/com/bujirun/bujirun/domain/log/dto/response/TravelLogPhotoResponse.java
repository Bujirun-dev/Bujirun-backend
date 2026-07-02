package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.log.entity.TravelLogPhoto;

import java.util.UUID;

public record TravelLogPhotoResponse(
        UUID id,
        String photoUrl,
        boolean representative
) {
    public static TravelLogPhotoResponse from(TravelLogPhoto photo) {
        return new TravelLogPhotoResponse(photo.getId(), photo.getPhotoUrl(), photo.isRepresentative());
    }
}
