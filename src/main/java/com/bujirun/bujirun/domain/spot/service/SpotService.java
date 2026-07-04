package com.bujirun.bujirun.domain.spot.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SpotService {

    private final TourSpotRepository tourSpotRepository;
    private final CollectionEntryRepository collectionEntryRepository;

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
}