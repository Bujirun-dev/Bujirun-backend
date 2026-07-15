package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.dto.request.*;
import com.bujirun.bujirun.domain.itinerary.dto.response.*;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryDayRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryItemRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.swipe.entity.SwipeSession;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import com.bujirun.bujirun.domain.visit.repository.VisitRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {

    private final ItineraryRepository        itineraryRepository;
    private final ItineraryDayRepository     itineraryDayRepository;
    private final ItineraryItemRepository    itineraryItemRepository;
    private final TourSpotRepository         tourSpotRepository;
    private final CollectionEntryRepository  collectionEntryRepository;
    private final VisitRepository            visitRepository;
    private final GroupMemberRepository      groupMemberRepository;
    private final SwipeSessionRepository     swipeSessionRepository;

    // ── Itinerary ──────────────────────────────────────────────────

    @Transactional
    public ItineraryDetailResponse create(CreateItineraryRequest req, UUID userId) {
        if (req.groupId() != null && !groupMemberRepository.existsById_GroupIdAndId_UserId(req.groupId(), userId)) {
            throw new IllegalArgumentException("그룹 멤버만 그룹 일정을 만들 수 있습니다.");
        }

        UUID sessionId = null;
        if (req.sessionId() != null) {
            SwipeSession session = swipeSessionRepository.findById(req.sessionId())
                    .orElseThrow(() -> new EntityNotFoundException("스와이프 세션을 찾을 수 없습니다. id=" + req.sessionId()));
            if (!session.getUserId().equals(userId)) {
                throw new IllegalArgumentException("본인의 스와이프 세션만 일정 생성에 사용할 수 있습니다.");
            }
            sessionId = session.getId();
        }

        Itinerary itinerary = Itinerary.builder()
                .userId(userId)
                .sessionId(sessionId)
                .groupId(req.groupId())
                .planType(req.planType() != null ? req.planType() : "A")
                .title(req.title())
                .startAt(req.startAt())
                .endAt(req.endAt())
                .build();
        return ItineraryDetailResponse.from(itineraryRepository.save(itinerary), Set.of(), Set.of());
    }

    public ItineraryDetailResponse getById(UUID id, UUID userId) {
        Itinerary itinerary = findWithDetails(id);
        validateAccess(itinerary, userId);
        return ItineraryDetailResponse.from(itinerary, fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    // 내 소유 일정 + 내가 속한 그룹의 공유 일정을 함께 반환
    public List<ItinerarySummaryResponse> getByUserId(UUID userId) {
        List<UUID> groupIds = groupMemberRepository.findById_UserId(userId).stream()
                .map(gm -> gm.getId().getGroupId())
                .toList();

        List<Itinerary> own = itineraryRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<Itinerary> grouped = groupIds.isEmpty()
                ? List.of()
                : itineraryRepository.findByGroupIdInOrderByCreatedAtDesc(groupIds);

        return Stream.concat(own.stream(), grouped.stream())
                .collect(Collectors.toMap(Itinerary::getId, i -> i, (a, b) -> a))
                .values().stream()
                .sorted(Comparator.comparing(Itinerary::getCreatedAt).reversed())
                .map(ItinerarySummaryResponse::from)
                .toList();
    }

    @Transactional
    public ItineraryDetailResponse update(UUID id, UpdateItineraryRequest req, UUID userId) {
        Itinerary itinerary = findWithDetails(id);
        validateAccess(itinerary, userId);
        if (req.title() != null)  itinerary.updateTitle(req.title());
        if (req.startAt() != null || req.endAt() != null) itinerary.updatePeriod(req.startAt(), req.endAt());
        if ("confirmed".equals(req.status())) itinerary.confirm();
        return ItineraryDetailResponse.from(itinerary, fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    // 일정 삭제는 그룹원 전체가 아니라 소유자만 가능 (공유 일정을 그룹원이 통째로 지울 수 없도록)
    @Transactional
    public void delete(UUID id, UUID userId) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + id));
        validateOwnerOnly(itinerary, userId);
        itineraryRepository.delete(itinerary);
    }

    // ── Day ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryDayResponse addDay(UUID itineraryId, AddDayRequest req, UUID userId) {
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + itineraryId));
        validateAccess(itinerary, userId);

        if (itineraryDayRepository.existsByItineraryIdAndDayNumber(itineraryId, req.dayNumber())) {
            throw new IllegalArgumentException("이미 존재하는 Day 번호입니다. dayNumber=" + req.dayNumber());
        }

        ItineraryDay day = ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(req.dayNumber())
                .date(req.date())
                .build();
        return ItineraryDayResponse.from(itineraryDayRepository.save(day), fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    @Transactional
    public void deleteDay(UUID itineraryId, UUID dayId, UUID userId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));
        validateAccess(day.getItinerary(), userId);
        itineraryDayRepository.delete(day);
    }

    // ── Item ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryItemResponse addItem(UUID itineraryId, UUID dayId, AddItemRequest req, UUID userId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));
        validateAccess(day.getItinerary(), userId);

        TourSpot spot = tourSpotRepository.findById(req.spotId())
                .orElseThrow(() -> new EntityNotFoundException("관광지를 찾을 수 없습니다. id=" + req.spotId()));

        ItineraryItem item = ItineraryItem.builder()
                .day(day)
                .spot(spot)
                .orderIndex(req.orderIndex())
                .arrivalTime(req.arrivalTime())
                .durationMin(req.durationMin())
                .travelMode(req.travelMode())
                .travelTimeMin(req.travelTimeMin())
                .memo(req.memo())
                .build();
        return ItineraryItemResponse.from(itineraryItemRepository.save(item), fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    @Transactional
    public ItineraryItemResponse updateItem(UUID itineraryId, UUID dayId, UUID itemId, UpdateItemRequest req, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateAccess(item.getDay().getItinerary(), userId);
        item.update(req.orderIndex(), req.arrivalTime(), req.durationMin(),
                req.travelMode(), req.travelTimeMin(), req.memo());
        return ItineraryItemResponse.from(item, fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    @Transactional
    public void deleteItem(UUID itineraryId, UUID dayId, UUID itemId, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateAccess(item.getDay().getItinerary(), userId);
        itineraryItemRepository.delete(item);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────

    private Set<UUID> fetchCollectedSpotIds(UUID userId) {
        return collectionEntryRepository.findByUserIdAndCollectedTrue(userId).stream()
                .map(e -> e.getSpot().getId())
                .collect(Collectors.toSet());
    }

    private Set<UUID> fetchVisitedSpotIds(UUID userId) {
        return Set.copyOf(visitRepository.findVerifiedSpotIdsByUserId(userId));
    }

    // 소유자 또는 그룹원이면 접근 허용 (읽기/수정/Day·Item 편집용)
    private void validateAccess(Itinerary itinerary, UUID userId) {
        if (itinerary.getUserId().equals(userId)) return;
        if (itinerary.getGroupId() != null
                && groupMemberRepository.existsById_GroupIdAndId_UserId(itinerary.getGroupId(), userId)) {
            return;
        }
        throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
    }

    // 소유자만 허용 (일정 삭제처럼 그룹원에게 열어주면 안 되는 동작용)
    private void validateOwnerOnly(Itinerary itinerary, UUID userId) {
        if (!itinerary.getUserId().equals(userId)) {
            throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
        }
    }

    private Itinerary findWithDetails(UUID id) {
        return itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + id));
    }

    private ItineraryItem findItem(UUID itineraryId, UUID dayId, UUID itemId) {
        return itineraryItemRepository.findById(itemId)
                .filter(i -> i.getDay().getId().equals(dayId)
                        && i.getDay().getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("항목을 찾을 수 없습니다. id=" + itemId));
    }
}
