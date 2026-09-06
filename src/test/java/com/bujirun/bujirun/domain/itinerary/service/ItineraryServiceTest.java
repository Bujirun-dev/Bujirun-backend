package com.bujirun.bujirun.domain.itinerary.service;

import com.bujirun.bujirun.domain.collection.repository.CollectionEntryRepository;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.service.GroupService;
import com.bujirun.bujirun.domain.itinerary.dto.request.AddItemRequest;
import com.bujirun.bujirun.domain.itinerary.dto.request.UpdateItemRequest;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.generate.service.SubwayScheduleMappingService;
import com.bujirun.bujirun.domain.itinerary.generate.service.TransitRouteService;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryDayRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryItemRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import com.bujirun.bujirun.domain.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
