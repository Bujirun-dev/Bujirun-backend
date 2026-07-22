package com.bujirun.bujirun.domain.log.service;

import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.dto.response.GroupMemberResponse;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.dto.response.ItineraryDetailResponse;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
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
import java.util.Set;
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
    private final GroupMemberRepository      groupMemberRepository;
    private final CollectionEntryRepository  collectionEntryRepository;

    // ── 로그 CRUD ──────────────────────────────────────────────────

    @Transactional
    public TravelLogDetailResponse create(CreateLogRequest req, UUID userId) {
        Itinerary itinerary = findItinerary(req.itineraryId());
        validateItineraryAccess(itinerary, userId);

        if (travelLogRepository.existsByItineraryIdAndUserId(req.itineraryId(), userId)) {
            throw new IllegalArgumentException("이미 이 일정에 대한 여행 기록을 작성했습니다. itineraryId=" + req.itineraryId());
        }

        TravelLog log = TravelLog.builder()
                .itineraryId(req.itineraryId())
                .userId(userId)
                .isPublic(req.isPublic())
                .mood(req.mood())
                .theme(req.theme())
                .travelNumber((int) travelLogRepository.countByUserId(userId) + 1)
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

        return TravelLogDetailResponse.of(log, itinerary, logItemMap, fetchGroupMembers(itinerary),
                countCollectedSpots(itinerary, log.getUserId()));
    }

    public TravelLogDetailResponse getDetail(UUID logId, UUID userId) {
        TravelLog log = findLog(logId);
        if (!log.isPublic() && !log.getUserId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        Itinerary itinerary = findItinerary(log.getItineraryId());
        Map<UUID, TravelLogItem> logItemMap = buildLogItemMap(logId);

        return TravelLogDetailResponse.of(log, itinerary, logItemMap, fetchGroupMembers(itinerary),
                countCollectedSpots(itinerary, log.getUserId()));
    }

    // 공개된(또는 본인) 여행 기록의 일정을 그대로 복제해 요청자 소유의 새 일정으로 생성
    @Transactional
    public ItineraryDetailResponse copyToItinerary(UUID logId, UUID userId) {
        TravelLog log = findLog(logId);
        if (!log.isPublic() && !log.getUserId().equals(userId)) {
            throw new IllegalArgumentException("접근 권한이 없습니다.");
        }

        Itinerary original = findItinerary(log.getItineraryId());

        Itinerary copy = Itinerary.builder()
                .userId(userId)
                .planType(original.getPlanType())
                .title(original.getTitle() + " (복사본)")
                .startAt(original.getStartAt())
                .startTime(original.getStartTime())
                .endAt(original.getEndAt())
                .endTime(original.getEndTime())
                .build();

        for (ItineraryDay day : original.getDays()) {
            ItineraryDay newDay = ItineraryDay.builder()
                    .itinerary(copy)
                    .dayNumber(day.getDayNumber())
                    .date(day.getDate())
                    .build();
            copy.getDays().add(newDay);

            for (ItineraryItem item : day.getItems()) {
                ItineraryItem newItem = ItineraryItem.builder()
                        .day(newDay)
                        .spot(item.getSpot())
                        .orderIndex(item.getOrderIndex())
                        .arrivalTime(item.getArrivalTime())
                        .durationMin(item.getDurationMin())
                        .travelMode(item.getTravelMode())
                        .travelTimeMin(item.getTravelTimeMin())
                        .memo(item.getMemo())
                        .build();
                newDay.getItems().add(newItem);
            }
        }

        return ItineraryDetailResponse.from(itineraryRepository.save(copy), Set.of(), Set.of());
    }

    public List<TravelLogSummaryResponse> getMyLogs(UUID userId) {
        String myNickname = userRepository.findById(userId).map(User::getNickname).orElse(null);
        return travelLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(log -> {
                    Itinerary itinerary = findItinerary(log.getItineraryId());
                    return TravelLogSummaryResponse.of(log, itinerary, myNickname,
                            countCollectedSpots(itinerary, log.getUserId()));
                })
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
                    Itinerary itinerary = findItinerary(log.getItineraryId());
                    return TravelLogSummaryResponse.of(log, itinerary, nickname,
                            countCollectedSpots(itinerary, log.getUserId()));
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
                    Itinerary itinerary = findItinerary(log.getItineraryId());
                    return TravelLogSummaryResponse.of(log, itinerary, nickname,
                            countCollectedSpots(itinerary, log.getUserId()));
                })
                .toList();
    }

    @Transactional
    public TravelLogDetailResponse update(UUID logId, UpdateLogRequest req, UUID userId) {
        TravelLog log = findLog(logId);
        validateLogOwner(log, userId);

        if (req.isPublic() != null) log.updateVisibility(req.isPublic());
        if (req.mood() != null || req.theme() != null) {
            log.updateReview(
                    req.mood() != null ? req.mood() : log.getMood(),
                    req.theme() != null ? req.theme() : log.getTheme());
        }

        Itinerary itinerary = findItinerary(log.getItineraryId());
        Map<UUID, TravelLogItem> logItemMap = buildLogItemMap(logId);

        return TravelLogDetailResponse.of(log, itinerary, logItemMap, fetchGroupMembers(itinerary),
                countCollectedSpots(itinerary, log.getUserId()));
    }

    @Transactional
    public void delete(UUID logId, UUID userId) {
        TravelLog log = findLog(logId);
        validateLogOwner(log, userId);
        travelLogRepository.delete(log);
    }
    // 회원탈퇴 시 본인의 모든 로그 비공개 처리
    @Transactional
    public void setUserLogsPrivate(UUID userId) {
        travelLogRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .forEach(log -> log.updateVisibility(false));
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

    // 소유자 또는 그룹원이면 자신의 여행 기록을 남길 수 있음
    private void validateItineraryAccess(Itinerary itinerary, UUID userId) {
        if (itinerary.getUserId().equals(userId)) return;
        if (itinerary.getGroupId() != null
                && groupMemberRepository.existsById_GroupIdAndId_UserId(itinerary.getGroupId(), userId)) {
            return;
        }
        throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
    }

    private Map<UUID, TravelLogItem> buildLogItemMap(UUID logId) {
        return travelLogItemRepository.findByTravelLogId(logId)
                .stream().collect(Collectors.toMap(TravelLogItem::getItineraryItemId, i -> i));
    }

    // 일정에 포함된 관광지 중 로그 작성자가 실제로 수집(방문 인증) 완료한 개수
    private int countCollectedSpots(Itinerary itinerary, UUID logOwnerId) {
        List<UUID> spotIds = itinerary.getDays().stream()
                .flatMap(d -> d.getItems().stream())
                .map(item -> item.getSpot().getId())
                .distinct()
                .toList();
        if (spotIds.isEmpty()) return 0;
        return (int) collectionEntryRepository.countCollectedByUserIdAndSpotIdIn(logOwnerId, spotIds);
    }

    private List<GroupMemberResponse> fetchGroupMembers(Itinerary itinerary) {
        if (itinerary.getGroupId() == null) return List.of();

        return groupMemberRepository.findById_GroupId(itinerary.getGroupId()).stream()
                .map(gm -> {
                    UUID memberId = gm.getId().getUserId();
                    String nickname = userRepository.findById(memberId).map(User::getNickname).orElse(null);
                    return new GroupMemberResponse(memberId, nickname, gm.getJoinedAt());
                })
                .toList();
    }
}
