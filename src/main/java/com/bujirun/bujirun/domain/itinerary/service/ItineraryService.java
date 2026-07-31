package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.dto.request.*;
import com.bujirun.bujirun.domain.itinerary.dto.response.*;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitRouteResponse;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
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

import java.time.LocalDate;
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
    private final TransitRouteService transitRouteService;
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
                .startTime(req.startTime())
                .endAt(req.endAt())
                .endTime(req.endTime())
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
        if (req.startAt() != null || req.endAt() != null) itinerary.updatePeriod(req.startAt(), req.startTime(), req.endAt(), req.endTime());
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

        LocalDate date = req.date();
        if (date == null && itinerary.getStartAt() != null) {
            date = itinerary.getStartAt().plusDays(req.dayNumber() - 1);
        }

        ItineraryDay day = ItineraryDay.builder()
                .itinerary(itinerary)
                .dayNumber(req.dayNumber())
                .date(date)
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

        // 프론트가 travelMode를 직접 안 보내면, 직전 스팟과의 구간을 자동 계산
        String travelMode = req.travelMode();
        Integer travelTimeMin = req.travelTimeMin();
        String routeType = null;
        String routeNo = null;
        String startStationName = null;
        String endStationName = null;
        String startArsId = null;

        if (travelMode == null) {
            ItineraryItem prevItem = day.getItems().stream()
                    .max(Comparator.comparing(ItineraryItem::getOrderIndex))
                    .orElse(null);

            if (prevItem != null) {
                List<SpotInfo> pair = List.of(toSpotInfo(prevItem.getSpot()), toSpotInfo(spot));
                List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(pair, null);

                if (!routes.isEmpty() && !routes.get(0).options().isEmpty()) {
                    TransitOption leg = routes.get(0).options().get(0);
                    SubPath firstSubPath = !leg.subPaths().isEmpty() ? leg.subPaths().get(0) : null;

                    travelMode = toTravelMode(leg.type());
                    travelTimeMin = leg.totalTime();
                    routeType = leg.type();
                    routeNo = firstSubPath != null ? firstSubPath.routeNo() : null;
                    startStationName = firstSubPath != null ? firstSubPath.startName() : null;
                    endStationName = firstSubPath != null ? firstSubPath.endName() : null;
                    startArsId = firstSubPath != null ? firstSubPath.startArsId() : null;
                }
            }
        }

        ItineraryItem item = ItineraryItem.builder()
                .day(day)
                .spot(spot)
                .orderIndex(req.orderIndex())
                .arrivalTime(req.arrivalTime())
                .durationMin(req.durationMin())
                .travelMode(travelMode)
                .travelTimeMin(travelTimeMin)
                .routeType(routeType)
                .routeNo(routeNo)
                .startStationName(startStationName)
                .endStationName(endStationName)
                .startArsId(startArsId)
                .memo(req.memo())
                .build();

        return ItineraryItemResponse.from(itineraryItemRepository.save(item), fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    private SpotInfo toSpotInfo(TourSpot spot) {
        return SpotInfo.builder()
                .contentId(spot.getContentId())
                .name(spot.getName())
                .category(spot.getCategory())
                .lat(spot.getLat() != null ? spot.getLat().doubleValue() : 0)
                .lng(spot.getLng() != null ? spot.getLng().doubleValue() : 0)
                .address(spot.getAddress())
                .thumbnailUrl(spot.getThumbnailUrl())
                .operatingHours(spot.getOperatingHours())
                .build();
    }

    // ODsay/자체계산 TransitOption.type()의 한글 값을 DB travel_mode 허용값(walk/transit/taxi)으로 변환
    private String toTravelMode(String type) {
        return switch (type) {
            case "도보" -> "walk";
            case "택시" -> "taxi";
            default -> "transit";
        };
    }

    @Transactional
    public ItineraryItemResponse updateItem(UUID itineraryId, UUID dayId, UUID itemId, UpdateItemRequest req, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateAccess(item.getDay().getItinerary(), userId);

        // travelMode만 오고 travelTimeMin이 없으면 = 사용자가 이동수단만 선택 → 재계산
        if (req.travelMode() != null && req.travelTimeMin() == null) {
            applyPreferredTravelMode(item, req.travelMode());
            item.update(req.orderIndex(), req.arrivalTime(), req.durationMin(),
                    item.getTravelMode(), item.getTravelTimeMin(), req.memo());
        } else {
            item.update(req.orderIndex(), req.arrivalTime(), req.durationMin(),
                    req.travelMode(), req.travelTimeMin(), req.memo());
        }

        return ItineraryItemResponse.from(item, fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    @Transactional
    public ItineraryItemResponse updateTravelMode(UUID itineraryId, UUID dayId, UUID itemId,
                                                  UpdateTravelModeRequest req, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateAccess(item.getDay().getItinerary(), userId);
        applyPreferredTravelModeStrict(item, req.travelMode());
        return ItineraryItemResponse.from(item, fetchCollectedSpotIds(userId), fetchVisitedSpotIds(userId));
    }

    // 사용자가 이동수단(walk/transit/taxi)만 선택했을 때, 직전 스팟과의 구간을 해당 수단 기준으로 재계산
    // 관대한 버전: updateItem()에서 호출. 재계산에 실패해도 예외를 던지지 않고 조용히 무시해서
    // orderIndex/arrivalTime/durationMin/memo 등 나머지 필드는 계속 저장되도록 한다.
    private void applyPreferredTravelMode(ItineraryItem item, String preferredMode) {
        List<ItineraryItem> dayItems = item.getDay().getItems(); // orderIndex ASC 정렬됨

        int idx = dayItems.indexOf(item);
        if (idx <= 0) return; // 첫 스팟은 이동정보 없음, 변경 대상 아님

        List<TransitOption> options = fetchLegOptions(dayItems.get(idx - 1), item);
        if (options.isEmpty()) return;

        TransitOption matched = options.stream()
                .filter(opt -> preferredMode.equals(toTravelMode(opt.type())))
                .findFirst()
                .orElse(options.get(0)); // 요청한 수단이 없으면 기본값(첫 옵션)으로 폴백

        applyMatchedOption(item, preferredMode, matched);
    }

    // 엄격한 버전: updateTravelMode()에서 호출. 사용자의 명시적 요청이므로 실패 시 명확한 예외를 던진다.
    private void applyPreferredTravelModeStrict(ItineraryItem item, String preferredMode) {
        List<ItineraryItem> dayItems = item.getDay().getItems(); // orderIndex ASC 정렬됨

        int idx = dayItems.indexOf(item);
        if (idx <= 0) {
            throw new IllegalArgumentException("첫 번째 방문 항목은 이동수단을 설정할 수 없습니다.");
        }

        List<TransitOption> options = fetchLegOptions(dayItems.get(idx - 1), item);
        if (options.isEmpty()) {
            throw new IllegalArgumentException("요청한 이동수단(" + preferredMode + ")의 경로를 찾을 수 없습니다.");
        }

        TransitOption matched = options.stream()
                .filter(opt -> preferredMode.equals(toTravelMode(opt.type())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "요청한 이동수단(" + preferredMode + ")의 경로를 찾을 수 없습니다."));

        applyMatchedOption(item, preferredMode, matched);
    }

    // 직전 항목과의 구간에 대한 이동수단 옵션 목록을 조회한다 (없으면 빈 리스트)
    private List<TransitOption> fetchLegOptions(ItineraryItem prevItem, ItineraryItem item) {
        List<SpotInfo> pair = List.of(toSpotInfo(prevItem.getSpot()), toSpotInfo(item.getSpot()));
        List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(pair, null);
        return routes.isEmpty() ? List.of() : routes.get(0).options();
    }

    // 선택된 옵션의 경로 상세(노선번호·정류장명 등)를 항목에 반영한다
    private void applyMatchedOption(ItineraryItem item, String preferredMode, TransitOption matched) {
        SubPath firstSubPath = !matched.subPaths().isEmpty() ? matched.subPaths().get(0) : null;

        item.updateRoute(
                preferredMode,
                matched.totalTime(),
                matched.type(),
                firstSubPath != null ? firstSubPath.routeNo() : null,
                firstSubPath != null ? firstSubPath.startName() : null,
                firstSubPath != null ? firstSubPath.endName() : null,
                firstSubPath != null ? firstSubPath.startArsId() : null
        );
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
