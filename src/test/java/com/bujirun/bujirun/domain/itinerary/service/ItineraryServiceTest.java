package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.service.GroupService;
import com.bujirun.bujirun.domain.itinerary.dto.request.AddItemRequest;
import com.bujirun.bujirun.domain.itinerary.dto.request.UpdateItemRequest;
import com.bujirun.bujirun.domain.itinerary.dto.request.UpdateItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.dto.response.ItineraryItemResponse;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.service.SubwayScheduleMappingService;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryDayRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryItemRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import com.bujirun.bujirun.domain.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ItineraryServiceTest {

    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final ItineraryDayRepository dayRepository = mock(ItineraryDayRepository.class);
    private final ItineraryItemRepository itemRepository = mock(ItineraryItemRepository.class);
    private final TourSpotRepository tourSpotRepository = mock(TourSpotRepository.class);
    private final CollectionEntryRepository collectionRepository = mock(CollectionEntryRepository.class);
    private final VisitRepository visitRepository = mock(VisitRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final GroupService groupService = mock(GroupService.class);
    private final SwipeSessionRepository swipeSessionRepository = mock(SwipeSessionRepository.class);
    private final TransitRouteService transitRouteService = mock(TransitRouteService.class);
    private final SubwayScheduleMappingService subwayScheduleMappingService = mock(SubwayScheduleMappingService.class);
    private final ItineraryService itineraryService = new ItineraryService(
            itineraryRepository, dayRepository, itemRepository, tourSpotRepository,
            collectionRepository, visitRepository, groupMemberRepository, groupService,
            swipeSessionRepository, transitRouteService, subwayScheduleMappingService);

    private final UUID itineraryId = UUID.randomUUID();
    private final UUID dayId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private ItineraryItem existingItem;

    @BeforeEach
    void setUp() {
        Itinerary itinerary = mock(Itinerary.class);
        when(itinerary.getId()).thenReturn(itineraryId);
        when(itinerary.getUserId()).thenReturn(userId);
        when(itinerary.getGroupId()).thenReturn(null);

        existingItem = mock(ItineraryItem.class);
        when(existingItem.getId()).thenReturn(UUID.randomUUID());
        when(existingItem.getArrivalTime()).thenReturn(LocalTime.of(10, 0));
        when(existingItem.getSpot()).thenReturn(spot("A", "관광지 A"));

        ItineraryDay day = mock(ItineraryDay.class);
        when(day.getItinerary()).thenReturn(itinerary);
        when(day.getItems()).thenReturn(List.of(existingItem));
        when(dayRepository.findByIdForUpdate(dayId)).thenReturn(Optional.of(day));
    }

    // ── 같은 날 같은 시각 허용 (프론트의 항목별 PATCH가 중간 상태에서 400을 맞지 않도록) ──

    @Test
    void 같은_날짜와_시간에도_일정을_추가할_수_있다() {
        TourSpot newSpot = spot("B", "관광지 B");
        when(tourSpotRepository.findById(newSpot.getId())).thenReturn(Optional.of(newSpot));
        when(itemRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        AddItemRequest request = new AddItemRequest(
                newSpot.getId(), 0, LocalTime.of(10, 0), null, null, null, null);

        ItineraryItemResponse response = itineraryService.addItem(itineraryId, dayId, request, userId);

        assertThat(response.arrivalTime()).isEqualTo(LocalTime.of(10, 0));
        verify(itemRepository).save(any());
    }

    @Test
    void 시간_변경으로_같은_날짜와_시간이_되어도_저장된다() {
        UUID targetItemId = UUID.randomUUID();
        ItineraryItem targetItem = mock(ItineraryItem.class);
        when(targetItem.getId()).thenReturn(targetItemId);
        when(targetItem.getSpot()).thenReturn(spot("B", "관광지 B"));

        ItineraryDay day = dayRepository.findByIdForUpdate(dayId).orElseThrow();
        when(day.getItems()).thenReturn(List.of(existingItem, targetItem));

        UpdateItemRequest request = new UpdateItemRequest(
                null, LocalTime.of(10, 0), null, null, null, null);

        assertThatCode(() -> itineraryService.updateItem(itineraryId, dayId, targetItemId, request, userId))
                .doesNotThrowAnyException();

        verify(targetItem).update(null, LocalTime.of(10, 0), null, null, null, null);
    }

    // ── 여행 시작 시각 변경 = 방문 시각 평행 이동 (AI 재최적화 아님) ──

    @Test
    void 시작_시간을_바꾸면_첫날_방문_시각이_같은_간격만큼_평행_이동한다() {
        ItineraryItem first = item("A", 0, LocalTime.of(9, 0));
        ItineraryItem second = item("B", 1, LocalTime.of(11, 0));
        Itinerary itinerary = itineraryWith(LocalTime.of(9, 0), LocalTime.of(20, 0), first, second);
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 10), LocalTime.of(12, 0),
                LocalDate.of(2026, 9, 10), LocalTime.of(20, 0),
                null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        assertThat(first.getArrivalTime()).isEqualTo(LocalTime.of(12, 0));
        assertThat(second.getArrivalTime()).isEqualTo(LocalTime.of(14, 0));
    }

    @Test
    void 평행_이동이_여행_종료_시각을_넘기면_상한에서_멈추고_시각이_겹치지_않는다() {
        ItineraryItem first = item("A", 0, LocalTime.of(9, 0));
        ItineraryItem second = item("B", 1, LocalTime.of(11, 0));
        ItineraryItem third = item("C", 2, LocalTime.of(13, 0));
        Itinerary itinerary = itineraryWith(LocalTime.of(9, 0), LocalTime.of(20, 0), first, second, third);
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        // 09:00 → 19:00 (+10시간). 그대로 밀면 21:00/23:00이 되지만 종료 시각(20:00)이 상한이고,
        // 상한에 몰려 같은 시각이 되지 않도록 최소 10분 간격으로 당겨진다.
        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 10), LocalTime.of(19, 0),
                LocalDate.of(2026, 9, 10), LocalTime.of(20, 0),
                null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        assertThat(List.of(first.getArrivalTime(), second.getArrivalTime(), third.getArrivalTime()))
                .containsExactly(LocalTime.of(19, 0), LocalTime.of(19, 50), LocalTime.of(20, 0));
    }

    @Test
    void 시작_시간이_그대로면_방문_시각을_건드리지_않는다() {
        ItineraryItem first = item("A", 0, LocalTime.of(9, 0));
        Itinerary itinerary = itineraryWith(LocalTime.of(9, 0), LocalTime.of(20, 0), first);
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                "제목만 변경", null, null, null, null, null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        assertThat(first.getArrivalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void 날짜_없이_시각만_보낸_요청도_기간에_반영된다() {
        ItineraryItem first = item("A", 0, LocalTime.of(9, 0));
        Itinerary itinerary = itineraryWith(LocalTime.of(9, 0), LocalTime.of(20, 0), first);
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, null, LocalTime.of(10, 0), null, null, null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        assertThat(itinerary.getStartTime()).isEqualTo(LocalTime.of(10, 0));
        assertThat(first.getArrivalTime()).isEqualTo(LocalTime.of(10, 0));
    }

    // ── 시작 > 종료 저장 차단 ──

    @Test
    void 같은_날짜에_종료_시각이_시작_시각보다_빠르면_수정을_거부한다() {
        Itinerary itinerary = itineraryWith(LocalTime.of(9, 0), LocalTime.of(20, 0),
                item("A", 0, LocalTime.of(9, 0)));
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 10), LocalTime.of(18, 0),
                LocalDate.of(2026, 9, 10), LocalTime.of(5, 0),
                null, null, null, null, null);

        assertThatThrownBy(() -> itineraryService.update(itineraryId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료 시간은 시작 시간보다 늦어야 합니다");
    }

    @Test
    void 종료일이_시작일보다_빠르면_수정을_거부한다() {
        Itinerary itinerary = itineraryWith(LocalTime.of(9, 0), LocalTime.of(20, 0),
                item("A", 0, LocalTime.of(9, 0)));
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 12), null,
                LocalDate.of(2026, 9, 10), null,
                null, null, null, null, null);

        assertThatThrownBy(() -> itineraryService.update(itineraryId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("종료일이 시작일보다 빠를 수 없습니다");
    }

    // ── 헬퍼 ──

    private TourSpot spot(String contentId, String name) {
        return TourSpot.builder().id(UUID.randomUUID()).contentId(contentId).name(name).build();
    }

    private ItineraryItem item(String contentId, int orderIndex, LocalTime arrivalTime) {
        return ItineraryItem.builder()
                .id(UUID.randomUUID())
                .spot(spot(contentId, "관광지 " + contentId))
                .orderIndex(orderIndex)
                .arrivalTime(arrivalTime)
                .build();
    }

    private Itinerary itineraryWith(LocalTime startTime, LocalTime endTime, ItineraryItem... items) {
        ItineraryDay day = ItineraryDay.builder().id(dayId).dayNumber(1)
                .items(new ArrayList<>(List.of(items))).build();
        return Itinerary.builder().id(itineraryId).userId(userId)
                .startAt(LocalDate.of(2026, 9, 10)).startTime(startTime)
                .endAt(LocalDate.of(2026, 9, 10)).endTime(endTime)
                .days(new ArrayList<>(List.of(day))).build();
    }
}
