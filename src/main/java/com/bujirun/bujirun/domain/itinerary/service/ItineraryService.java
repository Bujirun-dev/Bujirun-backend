package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
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
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {

    private final ItineraryRepository        itineraryRepository;
    private final ItineraryDayRepository     itineraryDayRepository;
    private final ItineraryItemRepository    itineraryItemRepository;
    private final TourSpotRepository         tourSpotRepository;
    private final CollectionEntryRepository  collectionEntryRepository;

    // ── Itinerary ──────────────────────────────────────────────────

    @Transactional
    public ItineraryDetailResponse create(CreateItineraryRequest req, UUID userId) {
        Itinerary itinerary = Itinerary.builder()
                .userId(userId)
                .sessionId(UUID.randomUUID())
                .planType(req.planType() != null ? req.planType() : "A")
                .title(req.title())
                .startAt(req.startAt())
                .endAt(req.endAt())
                .build();
        return ItineraryDetailResponse.from(itineraryRepository.save(itinerary), Set.of());
    }

    public ItineraryDetailResponse getById(UUID id, UUID userId) {
        Itinerary itinerary = findWithDetails(id);
        validateOwner(itinerary, userId);
        return ItineraryDetailResponse.from(itinerary, fetchCollectedSpotIds(userId));
    }

    public List<ItinerarySummaryResponse> getByUserId(UUID userId) {
        return itineraryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ItinerarySummaryResponse::from)
                .toList();
    }

    @Transactional
    public ItineraryDetailResponse update(UUID id, UpdateItineraryRequest req, UUID userId) {
        Itinerary itinerary = findWithDetails(id);
        validateOwner(itinerary, userId);
        if (req.title() != null)  itinerary.updateTitle(req.title());
        if (req.startAt() != null || req.endAt() != null) itinerary.updatePeriod(req.startAt(), req.endAt());
        if ("confirmed".equals(req.status())) itinerary.confirm();
        return ItineraryDetailResponse.from(itinerary, fetchCollectedSpotIds(userId));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + id));
        validateOwner(itinerary, userId);
        itineraryRepository.delete(itinerary);
    }

    // ── Day ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryDayResponse addDay(UUID itineraryId, AddDayRequest req, UUID userId) {
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + itineraryId));
        validateOwner(itinerary, userId);

        if (itineraryDayRepository.existsByItineraryIdAndDayNumber(itineraryId, req.dayNumber())) {
            throw new IllegalArgumentException("이미 존재하는 Day 번호입니다. dayNumber=" + req.dayNumber());
        }

        ItineraryDay day = ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(req.dayNumber())
                .date(req.date())
                .build();
        return ItineraryDayResponse.from(itineraryDayRepository.save(day), fetchCollectedSpotIds(userId));
    }

    @Transactional
    public void deleteDay(UUID itineraryId, UUID dayId, UUID userId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));
        validateOwner(day.getItinerary(), userId);
        itineraryDayRepository.delete(day);
    }

    // ── Item ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryItemResponse addItem(UUID itineraryId, UUID dayId, AddItemRequest req, UUID userId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));
        validateOwner(day.getItinerary(), userId);

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
        return ItineraryItemResponse.from(itineraryItemRepository.save(item), fetchCollectedSpotIds(userId));
    }

    @Transactional
    public ItineraryItemResponse updateItem(UUID itineraryId, UUID dayId, UUID itemId, UpdateItemRequest req, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateOwner(item.getDay().getItinerary(), userId);
        item.update(req.orderIndex(), req.arrivalTime(), req.durationMin(),
                req.travelMode(), req.travelTimeMin(), req.memo());
        return ItineraryItemResponse.from(item, fetchCollectedSpotIds(userId));
    }

    @Transactional
    public void deleteItem(UUID itineraryId, UUID dayId, UUID itemId, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateOwner(item.getDay().getItinerary(), userId);
        itineraryItemRepository.delete(item);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────

    private Set<UUID> fetchCollectedSpotIds(UUID userId) {
        return collectionEntryRepository.findByUserIdAndCollectedTrue(userId).stream()
                .map(e -> e.getSpot().getId())
                .collect(Collectors.toSet());
    }

    private void validateOwner(Itinerary itinerary, UUID userId) {
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
