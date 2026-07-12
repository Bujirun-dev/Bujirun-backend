package com.bujirun.bujirun.domain.collection.service;

import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.collection.dto.response.CollectionDetailResponse;
import com.bujirun.bujirun.domain.collection.dto.response.CollectionListResponse;
import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;
import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.spot.dto.response.SpotSearchResponse;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.auth.entity.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionEntryRepository collectionEntryRepository;
    private final TourSpotRepository tourSpotRepository;
    private final UserRepository userRepository;
    private static final List<String> DECK_CATEGORIES = List.of("바다", "자연", "문화", "체험");

    public List<CollectionListResponse> getCollectionBoard(UUID userId) {
        return collectionEntryRepository.findCollectionBoard(userId).stream()
                .map(CollectionListResponse::from)
                .toList();
    }

    public CollectionDetailResponse getDetail(UUID userId, UUID spotId) {
        CollectionEntry entry = collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new EntityNotFoundException("도감 기록을 찾을 수 없습니다. spotId=" + spotId));
        return CollectionDetailResponse.from(entry);
    }

    /**
     * VisitService에서 GPS 인증 성공 시 호출
     */
    @Transactional
    public void markCollected(UUID userId, UUID spotId) {
        TourSpot spot = tourSpotRepository.findById(spotId)
                .orElseThrow(() -> new EntityNotFoundException("관광지를 찾을 수 없습니다. id=" + spotId));

        if (!spot.isCollection()) {
            return;
        }

        User user = userRepository.getReferenceById(userId);

        CollectionEntry entry = collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseGet(() -> collectionEntryRepository.save(
                        CollectionEntry.builder().user(user).spot(spot).build()));

        entry.markCollected();
    }

    @Transactional
    public void cancel(UUID userId, UUID spotId) {
        CollectionEntry entry = collectionEntryRepository.findByUserIdAndSpotId(userId, spotId)
                .orElseThrow(() -> new EntityNotFoundException("도감 기록을 찾을 수 없습니다. spotId=" + spotId));
        entry.cancelCollection();
    }

    public List<SpotSearchResponse> getRandomSwipeDeck(UUID userId) {
        Set<UUID> collectedIds = collectionEntryRepository
                .findByUserIdAndCollectedTrue(userId)
                .stream()
                .map(ce -> ce.getSpot().getId())
                .collect(Collectors.toSet());

        List<TourSpot> deck = new ArrayList<>();

        for (int i = 0; i < DECK_CATEGORIES.size(); i++) {
            String category = DECK_CATEGORIES.get(i);
            List<TourSpot> pool = tourSpotRepository
                    .findByCollectionTrueAndCollectionCategory(category);
            Collections.shuffle(pool);

            int pickCount = i < 2 ? 3 : 2;
            pickCount = Math.min(pickCount, pool.size());

            deck.addAll(pool.subList(0, pickCount));
        }

        Collections.shuffle(deck);

        return deck.stream()
                .map(spot -> SpotSearchResponse.from(spot, collectedIds.contains(spot.getId()), false))
                .toList();
    }
}