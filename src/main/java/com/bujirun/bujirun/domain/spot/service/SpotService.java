package com.bujirun.bujirun.domain.spot.service;

import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;
import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.spot.client.TourApiClient;
import com.bujirun.bujirun.domain.spot.dto.response.SpotDetailResponse;
import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import com.bujirun.bujirun.domain.spot.dto.response.TourApiResponse;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.visit.repository.VisitRepository;
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
    private final VisitRepository visitRepository;
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

        // 방문 인증에 성공한 spotId 목록
        Set<UUID> visitedIds = new HashSet<>(visitRepository.findVerifiedSpotIdsByUserId(userId));

        List<TourSpot> spots = tourSpotRepository.searchSpots(keyword, sigunguId, category);

        if (!"NAME".equalsIgnoreCase(sort)) {
            // 추천순 — ①도감 대상 + 미수집 + 최다방문 카테고리 → ②도감 대상 + 미수집 → ③도감 대상(이미 수집) → ④일반 관광지
            String topCategory = visitRepository.findMostVisitedCategories(userId)
                    .stream()
                    .findFirst()
                    .orElse(null);

            spots = spots.stream()
                    .sorted(Comparator.comparingInt(
                            spot -> recommendTier(spot, collectedIds, topCategory)))
                    .toList();
        }
        // NAME이면 쿼리에서 이미 name asc로 나오니까 그대로

        return spots.stream()
                .map(spot -> SpotSearchResponse.from(
                        spot, collectedIds.contains(spot.getId()), visitedIds.contains(spot.getId())))
                .toList();
    }

    public SpotDetailResponse getDetail(UUID userId, UUID spotId) {
        TourSpot spot = tourSpotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("존재하지 않는 관광지입니다. spotId=" + spotId));

        Optional<TourApiResponse.DetailCommonResponse.CommonItem> apiDetail = Optional.empty();
        if (spot.getContentId() != null && !spot.getContentId().isBlank()) {
            apiDetail = tourApiClient.fetchDetailCommon(spot.getContentId());
        }

        boolean isCollected = collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .map(CollectionEntry::isCollected)
                .orElse(false);

        boolean isVisited = visitRepository.existsByUserIdAndSpotIdAndVerifiedTrue(userId, spotId);

        return SpotDetailResponse.of(spot, apiDetail.orElse(null), isCollected, isVisited);
    }

    // 추천순 정렬 우선순위 — 값이 작을수록 먼저 노출
    // 0: 도감 대상 + 미수집 + 사용자 최다방문 카테고리
    // 1: 도감 대상 + 미수집 (카테고리 무관)
    // 2: 도감 대상이지만 이미 수집 완료
    // 3: 도감 대상이 아닌 일반 관광지
    private int recommendTier(TourSpot spot, Set<UUID> collectedIds, String topCategory) {
        if (!spot.isCollection()) {
            return 3;
        }
        boolean collected = collectedIds.contains(spot.getId());
        if (collected) {
            return 2;
        }
        return (topCategory != null && topCategory.equals(spot.getCategory())) ? 0 : 1;
    }

    private boolean collectedByUser(UUID userId, UUID spotId) {
        return collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .map(CollectionEntry::isCollected)   // collected 필드명이 boolean getter로 isCollected()인지 확인 필요
                .orElse(false);
    }
}