package com.bujirun.bujirun.domain.swipe.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.vote.repository.ItineraryVoteSessionRepository;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.swipe.dto.response.SwipeStatusResponse;
import com.bujirun.bujirun.domain.swipe.repository.SwipeResultRepository;
import com.bujirun.bujirun.domain.swipe.repository.SwipeSessionRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SwipeServiceTest {

    private final SwipeSessionRepository swipeSessionRepository = mock(SwipeSessionRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final ItineraryVoteSessionRepository voteSessionRepository = mock(ItineraryVoteSessionRepository.class);
    private final SwipeService swipeService = new SwipeService(
            swipeSessionRepository, mock(SwipeResultRepository.class), mock(TourSpotRepository.class),
            groupMemberRepository, voteSessionRepository);

    @Test
    void 방장이_먼저_넘어가_생성이_시작되면_전원_완료_전이어도_generationStarted가_참이다() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(groupMemberRepository.existsById_GroupIdAndId_UserId(groupId, userId)).thenReturn(true);
        when(swipeSessionRepository.countDistinctCompletedUsersByGroupId(groupId)).thenReturn(1L);
        when(groupMemberRepository.countById_GroupId(groupId)).thenReturn(3L);
        when(voteSessionRepository.existsByGroupIdAndStatusIn(groupId, List.of("generating", "voting")))
                .thenReturn(true);

        SwipeStatusResponse status = swipeService.getSwipeStatus(groupId, userId);

        assertThat(status.isAllDone()).isFalse();
        assertThat(status.isGenerationStarted()).isTrue();
    }

    @Test
    void 생성_중이거나_투표_중인_세션이_없으면_generationStarted는_거짓이다() {
        UUID groupId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(groupMemberRepository.existsById_GroupIdAndId_UserId(groupId, userId)).thenReturn(true);
        when(swipeSessionRepository.countDistinctCompletedUsersByGroupId(groupId)).thenReturn(1L);
        when(groupMemberRepository.countById_GroupId(groupId)).thenReturn(3L);

        SwipeStatusResponse status = swipeService.getSwipeStatus(groupId, userId);

        assertThat(status.isGenerationStarted()).isFalse();
    }
}
