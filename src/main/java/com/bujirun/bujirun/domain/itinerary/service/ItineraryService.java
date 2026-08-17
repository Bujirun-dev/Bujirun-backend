package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.service.GroupService;
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
import com.bujirun.bujirun.global.util.TransitRouteUtils;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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
    private final GroupService               groupService;
    private final SwipeSessionRepository     swipeSessionRepository;
    private final TransitRouteService transitRouteService;
    // ── Itinerary ──────────────────────────────────────────────────

    @Transactional
    public ItineraryDetailResponse create(CreateItineraryRequest req, UUID userId) {
        if (req.groupId() != null) {
            if (!groupMemberRepository.existsById_GroupIdAndId_UserId(req.groupId(), userId)) {
                throw new IllegalArgumentException("그룹 멤버만 그룹 일정을 만들 수 있습니다.");
            }
            if (itineraryRepository.existsByGroupId(req.groupId())) {
                throw new IllegalArgumentException("이미 이 그룹의 일정이 존재합니다. 그룹당 일정은 하나만 만들 수 있습니다.");
            }
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

        List<Itinerary> own = itineraryRepository.findByUserIdAndGroupIdIsNullOrderByCreatedAtDesc(userId);
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

    // 일정 삭제는 개인 일정 전용. 그룹 일정은 나가기(leave)로만 정리 가능 (공유 일정을 통째로 지울 수 없도록)
    @Transactional
    public void delete(UUID id, UUID userId) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + id));
        validateOwnerOnly(itinerary, userId);
        if (itinerary.getGroupId() != null) {
            throw new IllegalArgumentException("그룹 일정은 삭제 대신 나가기를 사용해주세요.");
        }
        itineraryRepository.delete(itinerary);
    }

    // 그룹 일정 나가기. 그룹에 혼자 남은 상태에서 나가면 그룹과 일정이 함께 삭제된다.
    @Transactional
    public void leave(UUID id, UUID userId) {
        Itinerary itinerary = itineraryRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("일정을 찾을 수 없습니다. id=" + id));
        if (itinerary.getGroupId() == null) {
            throw new IllegalArgumentException("개인 일정은 나가기를 사용할 수 없습니다. 삭제를 이용해주세요.");
        }
        groupService.leave(itinerary.getGroupId(), userId);
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

        boolean alreadyInDay = day.getItems().stream()
                .anyMatch(existing -> existing.getSpot().getId().equals(spot.getId()));
        if (alreadyInDay) {
            throw new IllegalArgumentException("이미 해당 일차에 추가된 관광지입니다. spotId=" + req.spotId());
        }

        // 직전 스팟과의 구간 정보(역명/노선번호 등)는 항상 자동 계산한다.
        // travelMode는 프론트가 보낸 값이 있으면 그 수단에 맞는 옵션을 찾아서 쓰고,
        // 없으면(null) 자동 산출된 최적 옵션을 그대로 쓴다.
        String travelMode = req.travelMode();
        Integer travelTimeMin = req.travelTimeMin();
        String routeType = null;
        String routeNo = null;
        String startStationName = null;
        String endStationName = null;
        String startArsId = null;

        ItineraryItem prevItem = day.getItems().stream()
                .max(Comparator.comparing(ItineraryItem::getOrderIndex))
                .orElse(null);

        if (prevItem != null) {
            List<SpotInfo> pair = List.of(toSpotInfo(prevItem.getSpot()), toSpotInfo(spot));
            List<TransitRouteResponse> routes = transitRouteService.getRoutesForDay(pair, null);

            if (!routes.isEmpty() && !routes.get(0).options().isEmpty()) {
                List<TransitOption> options = routes.get(0).options();
                String requestedMode = travelMode;
                TransitOption leg = requestedMode != null
                        ? options.stream()
                                .filter(opt -> requestedMode.equals(toTravelMode(opt.type())))
                                .findFirst()
                                .orElse(options.get(0))
                        : options.get(0);
                SubPath firstSubPath = TransitRouteUtils.findFirstTransitSubPath(leg.subPaths());

                travelMode = toTravelMode(leg.type());
                if (travelTimeMin == null) travelTimeMin = leg.totalTime();
                // routeType: subPath 실측 타입(버스/지하철) 우선, 없으면(도보/택시 옵션) 옵션 타입 그대로
                routeType = firstSubPath != null ? firstSubPath.type() : leg.type();
                routeNo = firstSubPath != null ? firstSubPath.routeNo() : null;
                startStationName = firstSubPath != null ? firstSubPath.startName() : null;
                endStationName = firstSubPath != null ? firstSubPath.endName() : null;
                startArsId = firstSubPath != null ? firstSubPath.startArsId() : null;
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

    // 이동수단 변경 화면에서 확정 전 버스/지하철/택시/도보 후보의 실제 소요시간·요금을 미리 보여주기 위한 조회 API
    public List<TransitOption> getTravelModeOptions(UUID itineraryId, UUID dayId, UUID itemId, UUID userId) {
        ItineraryItem item = findItem(itineraryId, dayId, itemId);
        validateAccess(item.getDay().getItinerary(), userId);

        List<ItineraryItem> dayItems = item.getDay().getItems(); // orderIndex ASC 정렬됨
        int idx = dayItems.indexOf(item);
        if (idx <= 0) {
            throw new IllegalArgumentException("첫 번째 방문 항목은 이동수단 옵션이 없습니다.");
        }

        return fetchLegOptions(dayItems.get(idx - 1), item);
    }

    // 사용자가 이동수단(walk/transit/taxi)만 선택했을 때, 직전 스팟과의 구간을 해당 수단 기준으로 재계산
    // 관대한 버전: updateItem()에서 호출. 첫 스팟(idx<=0)은 애초에 이동정보가 없는 게 정상이라
    // 조용히 스킵하지만, 그 외의 실패(요청한 수단의 경로를 못 찾음)는 다른 수단으로 조용히
    // 바꿔치기하지 않고 예외를 던진다 — 그렇지 않으면 "지하철을 선택했는데 도보로 저장됨" 같은
    // 상황이 200 OK로 감춰져 버린다(2026-08-07 그룹 일정 버스/지하철 정보 조사에서 발견).
    private void applyPreferredTravelMode(ItineraryItem item, String preferredMode) {
        List<ItineraryItem> dayItems = item.getDay().getItems(); // orderIndex ASC 정렬됨

        int idx = dayItems.indexOf(item);
        if (idx <= 0) return; // 첫 스팟은 이동정보 없음, 변경 대상 아님

        List<TransitOption> options = fetchLegOptions(dayItems.get(idx - 1), item);
        TransitOption matched = options.stream()
                .filter(opt -> preferredMode.equals(toTravelMode(opt.type())))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "요청한 이동수단(" + preferredMode + ")의 경로를 찾을 수 없습니다."));

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
        SubPath firstSubPath = TransitRouteUtils.findFirstTransitSubPath(matched.subPaths());

        // routeType: subPath 실측 타입(버스/지하철) 우선, 없으면(도보/택시 옵션) 옵션 타입 그대로
        item.updateRoute(
                preferredMode,
                matched.totalTime(),
                firstSubPath != null ? firstSubPath.type() : matched.type(),
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

    // day에 속한 방문 항목 전체의 순서를 한 트랜잭션에서 원자적으로 재반영한다.
    // updateItem처럼 항목별로 나눠 PATCH하면, 그룹 일정에서 여러 클라이언트가 거의 동시에
    // flush할 때 서로 다른 순서 계산 결과가 겹쳐 쓰이며 order_index가 충돌할 수 있다
    // (2026-08-12 프로덕션 DB에서 실제로 같은 day_id+order_index 중복 확인). 클라이언트는
    // 항상 그 day의 전체 항목 id를 원하는 순서 그대로 보내야 하며, 일부만 보내거나
    // 다른 항목이 섞이면 거부한다 — 부분 반영 시 조용히 잘못된 최종 순서가 저장되는
    // 상황을 막기 위함.
    @Transactional
    public void reorderItems(UUID itineraryId, UUID dayId, ReorderItemsRequest req, UUID userId) {
        ItineraryDay day = itineraryDayRepository.findById(dayId)
                .filter(d -> d.getItinerary().getId().equals(itineraryId))
                .orElseThrow(() -> new EntityNotFoundException("Day를 찾을 수 없습니다. id=" + dayId));
        validateAccess(day.getItinerary(), userId);

        List<ItineraryItem> currentItems = day.getItems();
        Set<UUID> currentIds = currentItems.stream().map(ItineraryItem::getId).collect(Collectors.toSet());
        if (!currentIds.equals(Set.copyOf(req.itemIds())) || currentIds.size() != req.itemIds().size()) {
            throw new IllegalArgumentException("요청한 항목 목록이 현재 일차의 항목 구성과 일치하지 않습니다.");
        }

        Map<UUID, ItineraryItem> itemById = currentItems.stream()
                .collect(Collectors.toMap(ItineraryItem::getId, i -> i));
        for (int i = 0; i < req.itemIds().size(); i++) {
            itemById.get(req.itemIds().get(i)).updateOrder(i);
        }
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

    // 개인 일정은 소유자만, 그룹 일정은 현재 그룹 멤버만 접근 허용 (읽기/수정/Day·Item 편집용)
    // 그룹 일정에서는 만든 사람이라도 그룹을 나가면(leave) 더 이상 접근할 수 없어야 하므로 userId로 우회 허용하지 않는다.
    private void validateAccess(Itinerary itinerary, UUID userId) {
        if (itinerary.getGroupId() != null) {
            if (groupMemberRepository.existsById_GroupIdAndId_UserId(itinerary.getGroupId(), userId)) return;
            throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
        }
        if (itinerary.getUserId().equals(userId)) return;
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
