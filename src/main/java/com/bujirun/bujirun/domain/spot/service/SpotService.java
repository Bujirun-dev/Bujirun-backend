package com.bujirun.bujirun.domain.spot.service;

import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;
import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.spot.client.TourApiClient;
import com.bujirun.bujirun.domain.spot.dto.response.SpotDetailResponse;
import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import com.bujirun.bujirun.domain.spot.dto.response.TourApiResponse;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotService {

    private final TourSpotRepository tourSpotRepository;
    private final CollectionEntryRepository collectionEntryRepository;
    private final TourApiClient tourApiClient;

    public List<SpotSearchResponse> search(
            UUID userId, String keyword, Integer sigunguId,
            String category, String sort) {

        // 수집된 spotId 목록 (collection → spot 방향)
        Set<UUID> collectedIds = collectionEntryRepository
                .findByUserIdAndCollectedTrue(userId)
                .stream()
                .map(ce -> ce.getSpot().getId())
                .collect(Collectors.toSet());

        List<TourSpot> spots = tourSpotRepository.searchSpots(keyword, sigunguId, category);

        if (!"NAME".equalsIgnoreCase(sort)) {
            // 추천순 — 미수집 먼저
            spots = spots.stream()
                    .sorted(Comparator.comparing(
                            spot -> collectedIds.contains(spot.getId()) ? 1 : 0))
                    .toList();
        }
        // NAME이면 쿼리에서 이미 name asc로 나오니까 그대로

        return spots.stream()
                .map(spot -> SpotSearchResponse.from(spot, collectedIds.contains(spot.getId())))
                .toList();
    }

    public SpotDetailResponse getDetail(UUID userId, UUID spotId) {
        TourSpot spot = tourSpotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 관광지입니다. spotId=" + spotId));

        Optional<TourApiResponse.DetailCommonResponse.CommonItem> apiDetail = Optional.empty();
        if (spot.getContentTypeId() != null) {
            apiDetail = tourApiClient.fetchDetailCommon(spot.getContentId(), spot.getContentTypeId());
        }

        boolean isCollected = collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .map(CollectionEntry::isCollected)
                .orElse(false);

        return SpotDetailResponse.of(spot, apiDetail.orElse(null), isCollected);
    }

    private boolean collectedByUser(UUID userId, UUID spotId) {
        return collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .map(CollectionEntry::isCollected)   // collected 필드명이 boolean getter로 isCollected()인지 확인 필요
                .orElse(false);
    }
}