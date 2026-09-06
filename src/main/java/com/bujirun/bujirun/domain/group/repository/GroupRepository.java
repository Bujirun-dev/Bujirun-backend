package com.bujirun.bujirun.domain.group.repository;

import com.bujirun.bujirun.domain.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByInviteCode(String inviteCode);

    /**
     * "생성 중 이탈"로 방치된 그룹 id 목록.
     * 일정이 확정된 적이 없고(itineraries 미생성), 그룹 생성 이후 {@code cutoff} 시점까지
     * 스와이프/투표/멤버 합류 등 어떤 활동도 없었던 그룹만 고른다.
     * (스와이프만 하다 나갔거나 투표 단계에서 투표를 안 한 채 방치된 경우가 여기 해당)
     */
    @Query(value = """
            SELECT g.id FROM groups g
            WHERE g.created_at < :cutoff
              AND NOT EXISTS (SELECT 1 FROM itineraries i WHERE i.group_id = g.id)
              AND NOT EXISTS (SELECT 1 FROM group_members gm
                              WHERE gm.group_id = g.id AND gm.joined_at >= :cutoff)
              AND NOT EXISTS (SELECT 1 FROM swipe_sessions ss
                              WHERE ss.group_id = g.id AND ss.created_at >= :cutoff)
              AND NOT EXISTS (SELECT 1 FROM swipe_results sr
                              JOIN swipe_sessions s2 ON s2.id = sr.session_id
                              WHERE s2.group_id = g.id AND sr.swiped_at >= :cutoff)
              AND NOT EXISTS (SELECT 1 FROM itinerary_vote_sessions vs
                              WHERE vs.group_id = g.id AND vs.created_at >= :cutoff)
              AND NOT EXISTS (SELECT 1 FROM itinerary_votes v
                              JOIN itinerary_vote_sessions v2 ON v2.id = v.session_id
                              WHERE v2.group_id = g.id AND v.voted_at >= :cutoff)
            """, nativeQuery = true)
    List<UUID> findAbandonedGroupIds(@Param("cutoff") LocalDateTime cutoff);
}
