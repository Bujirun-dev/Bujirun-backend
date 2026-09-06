package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.group.dto.request.JoinGroupRequest;
import com.bujirun.bujirun.domain.group.dto.response.GroupInvitePreviewResponse;
import com.bujirun.bujirun.domain.group.entity.Group;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.repository.GroupRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class GroupServiceTest {

    private final GroupRepository groupRepository = mock(GroupRepository.class);
    private final GroupMemberRepository groupMemberRepository = mock(GroupMemberRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
    private final GroupService groupService = new GroupService(
            groupRepository, groupMemberRepository, userRepository, itineraryRepository);

    @Test
    void 완성된_일정의_초대_코드로는_그룹에_참여할_수_없다() {
        UUID groupId = UUID.randomUUID();
        Group group = mock(Group.class);
        when(group.getId()).thenReturn(groupId);
        when(groupRepository.findByInviteCode("DONE1234")).thenReturn(Optional.of(group));
        when(itineraryRepository.findFirstByGroupIdAndStatusOrderByCreatedAtDesc(groupId, "confirmed"))
                .thenReturn(Optional.of(mock(Itinerary.class)));

        assertThatThrownBy(() -> groupService.join(new JoinGroupRequest("DONE1234"), UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("이미 완성되어 참여가 종료된 일정");

        verify(groupMemberRepository, never()).save(any());
    }

    @Test
    void 초대_미리보기에_일정_완료_여부와_일정_id를_내려준다() {
        UUID groupId = UUID.randomUUID();
        UUID itineraryId = UUID.randomUUID();
        Group group = mock(Group.class);
        Itinerary itinerary = mock(Itinerary.class);
        when(group.getId()).thenReturn(groupId);
        when(group.getName()).thenReturn("부산 여행");
        when(groupRepository.findByInviteCode("DONE1234")).thenReturn(Optional.of(group));
        when(groupMemberRepository.countById_GroupId(groupId)).thenReturn(3L);
        when(itineraryRepository.findFirstByGroupIdAndStatusOrderByCreatedAtDesc(groupId, "confirmed"))
                .thenReturn(Optional.of(itinerary));
        when(itinerary.getId()).thenReturn(itineraryId);

        GroupInvitePreviewResponse response = groupService.previewByInviteCode("DONE1234");

        assertThat(response.completed()).isTrue();
        assertThat(response.itineraryId()).isEqualTo(itineraryId);
        assertThat(response.groupName()).isEqualTo("부산 여행");
    }
}
