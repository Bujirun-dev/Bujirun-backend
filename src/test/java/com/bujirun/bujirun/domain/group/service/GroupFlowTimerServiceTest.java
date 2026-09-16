package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.vote.entity.ItineraryVoteSession;
import com.bujirun.bujirun.domain.itinerary.vote.repository.ItineraryVoteSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GroupFlowTimerServiceTest {
    private final GroupMemberRepository members = mock(GroupMemberRepository.class);
    private final ItineraryVoteSessionRepository sessions = mock(ItineraryVoteSessionRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GroupFlowTimerService service = new GroupFlowTimerService(members, sessions, jdbc);
    private final UUID groupId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Test
    void rejectsNonMembersBeforeAccessingTimer() {
        assertThatThrownBy(() -> service.getOrStart(groupId, "waiting", null, userId))
                .isInstanceOf(EntityNotFoundException.class);
        verifyNoInteractions(jdbc, sessions);
    }

    @Test
    void rejectsInvalidPhaseAndMissingVoteSession() {
        when(members.existsById_GroupIdAndId_UserId(groupId, userId)).thenReturn(true);
        assertThatThrownBy(() -> service.getOrStart(groupId, "invite", null, userId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.getOrStart(groupId, "vote-waiting", null, userId))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsVoteSessionFromAnotherGroup() {
        when(members.existsById_GroupIdAndId_UserId(groupId, userId)).thenReturn(true);
        UUID sessionId = UUID.randomUUID();
        when(sessions.findById(sessionId)).thenReturn(Optional.of(
                ItineraryVoteSession.builder().groupId(UUID.randomUUID()).build()));
        assertThatThrownBy(() -> service.getOrStart(groupId, "vote-waiting", sessionId, userId))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(jdbc);
    }

    @Test
    void returnsStoredDeadlineAndDatabaseClockForEachMember() throws Exception {
        UUID guestId = UUID.randomUUID();
        when(members.existsById_GroupIdAndId_UserId(eq(groupId), any())).thenReturn(true);
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("deadline")).thenReturn(180000L);
        when(rs.getLong("now")).thenReturn(10000L, 45000L);
        when(jdbc.queryForObject(anyString(), any(RowMapper.class), eq(groupId), eq("waiting"), eq(groupId)))
                .thenAnswer(invocation -> {
                    RowMapper<?> mapper = invocation.getArgument(1);
                    return mapper.mapRow(rs, 0);
                });

        var host = service.getOrStart(groupId, "waiting", null, userId);
        var guest = service.getOrStart(groupId, "waiting", null, guestId);
        assertThat(host.deadlineAt()).isEqualTo(guest.deadlineAt());
        assertThat(guest.serverNow()).isEqualTo(45000L);
        verify(jdbc, times(2)).update(contains("DO NOTHING"), eq(groupId), eq("waiting"), eq(groupId));
    }

    @Test
    void scopesVoteTimerToItsSession() {
        UUID sessionId = UUID.randomUUID();
        when(members.existsById_GroupIdAndId_UserId(groupId, userId)).thenReturn(true);
        when(sessions.findById(sessionId)).thenReturn(Optional.of(
                ItineraryVoteSession.builder().groupId(groupId).build()));
        service.getOrStart(groupId, "vote-waiting", sessionId, userId);
        verify(jdbc).update(contains("DO NOTHING"), eq(groupId), eq("vote-waiting"), eq(sessionId));
    }
}
