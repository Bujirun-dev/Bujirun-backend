package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.service.GroupService;
import com.bujirun.bujirun.domain.itinerary.dto.request.AddItemRequest;
import com.bujirun.bujirun.domain.itinerary.dto.request.UpdateItemRequest;
import com.bujirun.bujirun.domain.itinerary.dto.request.UpdateItineraryRequest;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.service.SubwayScheduleMappingService;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
import com.bujirun.bujirun.domain.itinerary.optimize.dto.request.ItineraryOptimizeRequest;
import com.bujirun.bujirun.domain.itinerary.optimize.service.ItineraryOptimizeService;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
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
    private final ItineraryOptimizeService itineraryOptimizeService = mock(ItineraryOptimizeService.class);
    private final ItineraryService itineraryService = new ItineraryService(
            itineraryRepository, dayRepository, itemRepository, tourSpotRepository,
            collectionRepository, visitRepository, groupMemberRepository, groupService,
            swipeSessionRepository, transitRouteService, subwayScheduleMappingService,
            itineraryOptimizeService);

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

        ItineraryDay day = mock(ItineraryDay.class);
        when(day.getItinerary()).thenReturn(itinerary);
        when(day.getItems()).thenReturn(List.of(existingItem));
        when(dayRepository.findByIdForUpdate(dayId)).thenReturn(Optional.of(day));
    }

    @Test
    void 같은_날짜와_시간에_일정을_추가할_수_없다() {
        AddItemRequest request = new AddItemRequest(
                UUID.randomUUID(), 1, LocalTime.of(10, 0), null, null, null, null);

        assertThatThrownBy(() -> itineraryService.addItem(itineraryId, dayId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 날짜와 시간");

        verify(tourSpotRepository, never()).findById(any());
        verify(itemRepository, never()).save(any());
    }

    @Test
    void 시간_변경으로_같은_날짜와_시간이_되는_것도_막는다() {
        UUID targetItemId = UUID.randomUUID();
        ItineraryItem targetItem = mock(ItineraryItem.class);
        when(targetItem.getId()).thenReturn(targetItemId);

        ItineraryDay day = dayRepository.findByIdForUpdate(dayId).orElseThrow();
        when(day.getItems()).thenReturn(List.of(existingItem, targetItem));

        UpdateItemRequest request = new UpdateItemRequest(
                null, LocalTime.of(10, 0), null, null, null, null);

        assertThatThrownBy(() -> itineraryService.updateItem(
                itineraryId, dayId, targetItemId, request, userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("같은 날짜와 시간");

        verify(targetItem, never()).update(any(), any(), any(), any(), any(), any());
    }

    @Test
    void 시작_시간을_바꾸면_아이템이_있는_day를_새_시작_시각으로_재최적화한다() {
        ItineraryItem item = ItineraryItem.builder()
                .id(UUID.randomUUID())
                .spot(TourSpot.builder().id(UUID.randomUUID()).contentId("A").name("관광지 A").build())
                .orderIndex(1).arrivalTime(LocalTime.of(9, 0)).build();

        ItineraryDay day = ItineraryDay.builder().id(dayId).dayNumber(1)
                .items(new ArrayList<>(List.of(item))).build();
        Itinerary itinerary = Itinerary.builder().id(itineraryId).userId(userId)
                .startAt(LocalDate.of(2026, 9, 10)).startTime(LocalTime.of(9, 0))
                .endAt(LocalDate.of(2026, 9, 10)).endTime(LocalTime.of(20, 0))
                .days(new ArrayList<>(List.of(day))).build();
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 10), LocalTime.of(18, 0),
                LocalDate.of(2026, 9, 10), LocalTime.of(5, 0),
                null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        verify(itineraryOptimizeService).optimizeDay(eq(dayId),
                argThat(r -> LocalTime.of(18, 0).equals(r.getStartTime())), eq(userId));
    }

    @Test
    void 시작_시간이_그대로면_재최적화를_호출하지_않는다() {
        ItineraryItem item = ItineraryItem.builder()
                .id(UUID.randomUUID())
                .spot(TourSpot.builder().id(UUID.randomUUID()).contentId("A").name("관광지 A").build())
                .orderIndex(1).arrivalTime(LocalTime.of(9, 0)).build();

        ItineraryDay day = ItineraryDay.builder().id(dayId).dayNumber(1)
                .items(new ArrayList<>(List.of(item))).build();
        Itinerary itinerary = Itinerary.builder().id(itineraryId).userId(userId)
                .startAt(LocalDate.of(2026, 9, 10)).startTime(LocalTime.of(9, 0))
                .endAt(LocalDate.of(2026, 9, 10)).endTime(LocalTime.of(20, 0))
                .days(new ArrayList<>(List.of(day))).build();
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                "제목만 변경", null, null, null, null, null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        verify(itineraryOptimizeService, never()).optimizeDay(any(), any(), any());
    }

    @Test
    void 재최적화로_orderIndex가_바뀌면_응답에_반영되도록_아이템_목록도_다시_정렬한다() {
        ItineraryItem itemA = ItineraryItem.builder()
                .id(UUID.randomUUID())
                .spot(TourSpot.builder().id(UUID.randomUUID()).contentId("A").name("관광지 A").build())
                .orderIndex(1).arrivalTime(LocalTime.of(9, 0)).build();
        ItineraryItem itemB = ItineraryItem.builder()
                .id(UUID.randomUUID())
                .spot(TourSpot.builder().id(UUID.randomUUID()).contentId("B").name("관광지 B").build())
                .orderIndex(2).arrivalTime(LocalTime.of(11, 0)).build();

        ItineraryDay day = ItineraryDay.builder().id(dayId).dayNumber(1)
                .items(new ArrayList<>(List.of(itemA, itemB))).build();
        Itinerary itinerary = Itinerary.builder().id(itineraryId).userId(userId)
                .startAt(LocalDate.of(2026, 9, 10)).startTime(LocalTime.of(9, 0))
                .endAt(LocalDate.of(2026, 9, 10)).endTime(LocalTime.of(20, 0))
                .days(new ArrayList<>(List.of(day))).build();
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        // optimizeDay가 좌표 기준으로 B를 먼저 방문하도록 순서를 바꿨다고 가정(실제 로직은 목으로 대체)
        doAnswer(invocation -> {
            itemB.updateOrder(1);
            itemA.updateOrder(2);
            return null;
        }).when(itineraryOptimizeService).optimizeDay(eq(dayId), any(), eq(userId));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 10), LocalTime.of(18, 0),
                LocalDate.of(2026, 9, 10), LocalTime.of(5, 0),
                null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        assertThat(day.getItems()).containsExactly(itemB, itemA);
    }

    @Test
    void 아이템이_없는_day는_재최적화를_호출하지_않는다() {
        ItineraryDay emptyDay = ItineraryDay.builder().id(dayId).dayNumber(1)
                .items(new ArrayList<>()).build();
        Itinerary itinerary = Itinerary.builder().id(itineraryId).userId(userId)
                .startAt(LocalDate.of(2026, 9, 10)).startTime(LocalTime.of(9, 0))
                .endAt(LocalDate.of(2026, 9, 10)).endTime(LocalTime.of(20, 0))
                .days(new ArrayList<>(List.of(emptyDay))).build();
        when(itineraryRepository.findById(itineraryId)).thenReturn(Optional.of(itinerary));

        UpdateItineraryRequest request = new UpdateItineraryRequest(
                null, LocalDate.of(2026, 9, 10), LocalTime.of(18, 0),
                LocalDate.of(2026, 9, 10), LocalTime.of(5, 0),
                null, null, null, null, null);

        itineraryService.update(itineraryId, request, userId);

        verify(itineraryOptimizeService, never()).optimizeDay(any(), any(), any());
    }
}
