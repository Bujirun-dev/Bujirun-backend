package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.vote.repository.ItineraryVoteSessionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GroupFlowTimerService {
    private final GroupMemberRepository members;
    private final ItineraryVoteSessionRepository sessions;
    private final JdbcTemplate jdbc;

    public record TimerResponse(long deadlineAt, long serverNow) {}

    // 첫 참여자의 대기 진입 시 한 번만 시작한다. 재진입/동시 요청은 기존 마감을 재사용한다.
    @Transactional
    public TimerResponse getOrStart(UUID groupId, String phase, UUID sessionId, UUID userId) {
        if (!members.existsById_GroupIdAndId_UserId(groupId, userId)) {
            throw new EntityNotFoundException("참여 중인 그룹이 아닙니다.");
        }
        UUID scopeId;
        if ("waiting".equals(phase)) {
            scopeId = groupId;
        } else if ("vote-waiting".equals(phase) && sessionId != null) {
            var session = sessions.findById(sessionId)
                    .orElseThrow(() -> new EntityNotFoundException("투표 세션을 찾을 수 없습니다."));
            if (!groupId.equals(session.getGroupId())) {
                throw new IllegalArgumentException("그룹의 투표 세션이 아닙니다.");
            }
            scopeId = sessionId;
        } else {
            throw new IllegalArgumentException("대기 단계와 투표 세션을 확인해주세요.");
        }

        jdbc.update("""
                INSERT INTO group_flow_timers (group_id, phase, scope_id, deadline_at)
                VALUES (?, ?, ?, clock_timestamp() + interval '3 minutes')
                ON CONFLICT (group_id, phase, scope_id) DO NOTHING
                """, groupId, phase, scopeId);
        // DB 시계도 함께 반환하여 휴대폰/PC의 시계 오차를 제거한다.
        return jdbc.queryForObject("""
                SELECT (extract(epoch FROM deadline_at) * 1000)::bigint AS deadline,
                       (extract(epoch FROM clock_timestamp()) * 1000)::bigint AS now
                FROM group_flow_timers WHERE group_id = ? AND phase = ? AND scope_id = ?
                """, (rs, row) -> new TimerResponse(rs.getLong("deadline"), rs.getLong("now")),
                groupId, phase, scopeId);
    }
}
