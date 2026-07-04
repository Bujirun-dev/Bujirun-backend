package com.bujirun.bujirun.domain.log.service;

import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryItemRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import com.bujirun.bujirun.domain.log.dto.request.AddHashtagRequest;
import com.bujirun.bujirun.domain.log.dto.request.AddPhotoRequest;
import com.bujirun.bujirun.domain.log.dto.request.CreateLogRequest;
import com.bujirun.bujirun.domain.log.dto.request.UpdateLogRequest;
import com.bujirun.bujirun.domain.log.dto.response.*;
import com.bujirun.bujirun.domain.log.entity.*;
import com.bujirun.bujirun.domain.log.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TravelLogService {

    private final TravelLogRepository        travelLogRepository;
    private final TravelLogItemRepository    travelLogItemRepository;
    private final TravelLogPhotoRepository   travelLogPhotoRepository;
    private final TravelLogHashtagRepository travelLogHashtagRepository;
    private final ItineraryRepository        itineraryRepository;
    private final ItineraryItemRepository    itineraryItemRepository;
    private final UserRepository             userRepository;

    // ── 로그 CRUD ──────────────────────────────────────────────────

    @Transactional
    public TravelLogDetailResponse create(CreateLogRequest req, UUID userId) {
        Itinerary itinerary = findItinerary(req.itineraryId());
        validateItineraryOwner(itinerary, userId);

        TravelLog log = TravelLog.builder()
                .itineraryId(req.itineraryId())
                .userId(userId)
                .isPublic(req.isPublic())
                .build();
        travelLogRepository.save(log);

        // 이티너리의 모든 아이템에 대해 TravelLogItem 생성
        for (var day : itinerary.getDays()) {
            for (ItineraryItem item : day.getItems()) {
                TravelLogItem logItem = TravelLogItem.builder()
                        .travelLog(log)
                        .itineraryItemId(item.getId())
                        .build();
                travelLogItemRepository.save(logItem);
            }
        }

        Map<UUID, TravelLogItem> logItemMap = travelLogItemRepository.findByTravelLogId(log.getId())
                .stream().collect(Collectors.toMap(TravelLogItem::getItineraryItemId, i -> i));

        return TravelLogDetailResponse.of(log, itinerary, logItemMap);
    }

    public TravelLogDetailResponse getDetail(UUID logId, UUID userId) {
        TravelLog log = findLog(logId);
        if (!log.isPublic() && !log.getUserId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        Itinerary itinerary = findItinerary(log.getItineraryId());
        Map<UUID, TravelLogItem> logItemMap = buildLogItemMap(logId);

        return TravelLogDetailResponse.of(log, itinerary, logItemMap);
    }

    public List<TravelLogSummaryResponse> getMyLogs(UUID userId) {
        String myNickname = userRepository.findById(userId).map(User::getNickname).orElse(null);
        return travelLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(log -> TravelLogSummaryResponse.of(log, findItinerary(log.getItineraryId()), myNickname))
                .toList();
    }

    public List<TravelLogSummaryResponse> getPublicLogs(String category, String sort) {
        List<TravelLog> logs;

        if (category != null && !category.equals("전체")) {
            List<UUID> itemIds = itineraryItemRepository.findIdsBySpotCategory(category);
            if (itemIds.isEmpty()) return List.of();
            List<UUID> logIds = travelLogItemRepository.findDistinctTravelLogIdsByItineraryItemIdIn(itemIds);
            if (logIds.isEmpty()) return List.of();
            logs = "popular".equals(sort)
                    ? travelLogRepository.findByIdInAndIsPublicTrueOrderByAddedCountDesc(logIds)
                    : travelLogRepository.findByIdInAndIsPublicTrueOrderByCreatedAtDesc(logIds);
        } else {
            logs = "popular".equals(sort)
                    ? travelLogRepository.findByIsPublicTrueOrderByAddedCountDesc()
                    : travelLogRepository.findByIsPublicTrueOrderByCreatedAtDesc();
        }

        return logs.stream()
                .map(log -> {
                    String nickname = userRepository.findById(log.getUserId()).map(User::getNickname).orElse(null);
                    return TravelLogSummaryResponse.of(log, findItinerary(log.getItineraryId()), nickname);
                })
                .toList();
    }

    public List<TravelLogSummaryResponse> getLogsBySpotId(UUID spotId) {
        List<UUID> itemIds = itineraryItemRepository.findIdsBySpotId(spotId);
        if (itemIds.isEmpty()) return List.of();
        List<UUID> logIds = travelLogItemRepository.findDistinctTravelLogIdsByItineraryItemIdIn(itemIds);
        if (logIds.isEmpty()) return List.of();

        return travelLogRepository.findByIdInAndIsPublicTrueOrderByCreatedAtDesc(logIds).stream()
                .map(log -> {
                    String nickname = userRepository.findById(log.getUserId()).map(User::getNickname).orElse(null);
                    return TravelLogSummaryResponse.of(log, findItinerary(log.getItineraryId()), nickname);
                })
                .toList();
    }

    @Transactional
    public TravelLogDetailResponse update(UUID logId, UpdateLogRequest req, UUID userId) {
        TravelLog log = findLog(logId);
        validateLogOwner(log, userId);

        if (req.isPublic() != null) log.updateVisibility(req.isPublic());

        Itinerary itinerary = findItinerary(log.getItineraryId());
        Map<UUID, TravelLogItem> logItemMap = buildLogItemMap(logId);

        return TravelLogDetailResponse.of(log, itinerary, logItemMap);
    }

    @Transactional
    public void delete(UUID logId, UUID userId) {
        TravelLog log = findLog(logId);
        validateLogOwner(log, userId);
        travelLogRepository.delete(log);
    }

    // ── 사진 ────────────────────────────────────────────────────────

    @Transactional
    public TravelLogPhotoResponse addPhoto(UUID logId, UUID logItemId, AddPhotoRequest req, UUID userId) {
        TravelLogItem logItem = findLogItem(logId, logItemId, userId);

        int nextOrder = logItem.getPhotos().size();
        TravelLogPhoto photo = TravelLogPhoto.builder()
                .travelLogItem(logItem)
                .photoUrl(req.photoUrl())
                .orderIndex(nextOrder)
                .build();

        return TravelLogPhotoResponse.from(travelLogPhotoRepository.save(photo));
    }

    @Transactional
    public void deletePhoto(UUID logId, UUID logItemId, UUID photoId, UUID userId) {
        findLogItem(logId, logItemId, userId);
        TravelLogPhoto photo = travelLogPhotoRepository.findById(photoId)
                .filter(p -> p.getTravelLogItem().getId().equals(logItemId))
                .orElseThrow(() -> new EntityNotFoundException("사진을 찾을 수 없습니다. id=" + photoId));

        if (photo.isRepresentative()) {
            findLog(logId).updateThumbnail(null);
        }
        travelLogPhotoRepository.delete(photo);
    }

    @Transactional
    public TravelLogPhotoResponse setRepresentativePhoto(UUID logId, UUID logItemId, UUID photoId, UUID userId) {
        findLogItem(logId, logItemId, userId);
        TravelLogPhoto photo = travelLogPhotoRepository.findById(photoId)
                .filter(p -> p.getTravelLogItem().getId().equals(logItemId))
                .orElseThrow(() -> new EntityNotFoundException("사진을 찾을 수 없습니다. id=" + photoId));

        travelLogPhotoRepository.clearRepresentativeByLog(logId);
        photo.setRepresentative(true);
        findLog(logId).updateThumbnail(photo.getPhotoUrl());

        return TravelLogPhotoResponse.from(photo);
    }

    // ── 해시태그 ────────────────────────────────────────────────────

    @Transactional
    public TravelLogHashtagResponse addHashtag(UUID logId, UUID logItemId, AddHashtagRequest req, UUID userId) {
        TravelLogItem logItem = findLogItem(logId, logItemId, userId);

        TravelLogHashtag hashtag = TravelLogHashtag.builder()
                .travelLogItem(logItem)
                .tag(req.tag())
                .build();

        return TravelLogHashtagResponse.from(travelLogHashtagRepository.save(hashtag));
    }

    @Transactional
    public void deleteHashtag(UUID logId, UUID logItemId, UUID hashtagId, UUID userId) {
        findLogItem(logId, logItemId, userId);
        TravelLogHashtag hashtag = travelLogHashtagRepository.findById(hashtagId)
                .filter(h -> h.getTravelLogItem().getId().equals(logItemId))
                .orElseThrow(() -> new EntityNotFoundException("해시태그를 찾을 수 없습니다. id=" + hashtagId));
        travelLogHashtagRepository.delete(hashtag);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────

    private TravelLog findLog(UUID logId) {
        return travelLogRepository.findById(logId)
                .orElseThrow(() -> new EntityNotFoundException("로그를 찾을 수 없습니다. id=" + logId));
    }

    private Itinerary findItinerary(UUID itineraryId) {
        return itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + itineraryId));
    }

    private TravelLogItem findLogItem(UUID logId, UUID logItemId, UUID userId) {
        validateLogOwner(findLog(logId), userId);
        return travelLogItemRepository.findById(logItemId)
                .filter(i -> i.getTravelLog().getId().equals(logId))
                .orElseThrow(() -> new EntityNotFoundException("로그 아이템을 찾을 수 없습니다. id=" + logItemId));
    }

    private void validateLogOwner(TravelLog log, UUID userId) {
        if (!log.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 로그에 대한 권한이 없습니다.");
        }
    }

    private void validateItineraryOwner(Itinerary itinerary, UUID userId) {
        if (!itinerary.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
        }
    }

    private Map<UUID, TravelLogItem> buildLogItemMap(UUID logId) {
        return travelLogItemRepository.findByTravelLogId(logId)
                .stream().collect(Collectors.toMap(TravelLogItem::getItineraryItemId, i -> i));
    }
}
