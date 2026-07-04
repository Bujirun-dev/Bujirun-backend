package com.bujirun.bujirun.domain.collection.service;

import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.collection.dto.response.CollectionDetailResponse;
import com.bujirun.bujirun.domain.collection.dto.response.CollectionListResponse;
import com.bujirun.bujirun.domain.collection.entity.CollectionEntry;
import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.auth.entity.User;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollectionService {

    private final CollectionEntryRepository collectionEntryRepository;
    private final TourSpotRepository tourSpotRepository;
    private final UserRepository userRepository;

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
}