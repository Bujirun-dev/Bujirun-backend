package com.bujirun.bujirun.domain.log.dto.response;

import com.bujirun.bujirun.domain.log.entity.TravelLogHashtag;

import java.util.UUID;

public record TravelLogHashtagResponse(
        UUID id,
        String tag
) {
    public static TravelLogHashtagResponse from(TravelLogHashtag hashtag) {
        return new TravelLogHashtagResponse(hashtag.getId(), hashtag.getTag());
    }
}
