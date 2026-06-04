package com.bujirun.bujirun.domain.itinerary.service;

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
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItineraryService {

    private final ItineraryRepository     itineraryRepository;
    private final ItineraryDayRepository  itineraryDayRepository;
    private final ItineraryItemRepository itineraryItemRepository;
    private final TourSpotRepository      tourSpotRepository;

    // ── Itinerary ──────────────────────────────────────────────────

    @Transactional
    public ItineraryDetailResponse create(CreateItineraryRequest req) {
        Itinerary itinerary = Itinerary.builder()
                .userId(req.userId())
                .planType(req.planType() != null ? req.planType() : "A")
                .title(req.title())
                .build();
        return ItineraryDetailResponse.from(itineraryRepository.save(itinerary));
    }

    public ItineraryDetailResponse getById(UUID id) {
        return ItineraryDetailResponse.from(findWithDetails(id));
    }

    public List<ItinerarySummaryResponse> getByUserId(UUID userId) {
        return itineraryRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(ItinerarySummaryResponse::from)
                .toList();
    }

    @Transactional
    public ItineraryDetailResponse update(UUID id, UpdateItineraryRequest req) {
        Itinerary itinerary = findWithDetails(id);
        if (req.title() != null)  itinerary.updateTitle(req.title());
        if ("confirmed".equals(req.status())) itinerary.confirm();
        return ItineraryDetailResponse.from(itinerary);
    }

    @Transactional
    public void delete(UUID id) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + id));
        itineraryRepository.delete(itinerary);
    }

    // ── Day ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryDayResponse addDay(UUID itineraryId, AddDayRequest req) {
        Itinerary itinerary = itineraryRepository.findById(itineraryId)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + itineraryId));

        if (itineraryDayRepository.existsByItineraryIdAndDayNumber(itineraryId, req.dayNumber())) {
            throw new IllegalArgumentException("이미 존재하는 Day 번호입니다. dayNumber=" + req.dayNumber());
        }

        ItineraryDay day = ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(req.dayNumber())
                .date(req.date())
                .build();
        return ItineraryDayResponse.from(itineraryDayRepository.save(day));
    }

    @Transactional
    public void deleteDay(UUID itineraryId, UUID dayId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));
        itineraryDayRepository.delete(day);
    }

    // ── Item ────────────────────────────────────────────────────────

    @Transactional
    public ItineraryItemResponse addItem(UUID itineraryId, UUID dayId, AddItemRequest req) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));

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
        return ItineraryItemResponse.from(itineraryItemRepository.save(item));
    }

    @Transactional
    public ItineraryItemResponse updateItem(UUID itineraryId, UUID dayId, UUID itemId, UpdateItemRequest req) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        item.update(req.orderIndex(), req.arrivalTime(), req.durationMin(),
                req.travelMode(), req.travelTimeMin(), req.memo());
        return ItineraryItemResponse.from(item);
    }

    @Transactional
    public void deleteItem(UUID itineraryId, UUID dayId, UUID itemId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        itineraryItemRepository.delete(item);
    }

    // ── 내부 헬퍼 ──────────────────────────────────────────────────

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
