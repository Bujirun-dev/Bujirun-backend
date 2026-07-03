package com.bujirun.bujirun.domain.itinerary.generate.repository;

import com.bujirun.bujirun.domain.itinerary.generate.dto.projection.SpotSwipeAggregate;
import com.bujirun.bujirun.domain.itinerary.generate.entity.SwipeResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface SwipeResultRepository extends JpaRepository<SwipeResult, UUID> {

    /**
     * 그룹 내 각 유저의 "가장 최근 completed 세션" 기준으로
     * spot별 liked count / total count 를 집계한다.
     * (한 유저가 재스와이프 했을 경우 최신 세션만 반영하기 위함)
     */
    @Query("""
            SELECT sr.spot.id AS spotId,
                   SUM(CASE WHEN sr.liked = true THEN 1 ELSE 0 END) AS likedCount,
                   COUNT(sr) AS totalCount
            FROM SwipeResult sr
            WHERE sr.session.id IN (
                SELECT s.id FROM SwipeSession s
                WHERE s.groupId = :groupId
                  AND s.status = 'completed'
                  AND s.createdAt = (
                      SELECT MAX(s2.createdAt) FROM SwipeSession s2
                      WHERE s2.groupId = :groupId
                        AND s2.userId = s.userId
                        AND s2.status = 'completed'
                  )
            )
            GROUP BY sr.spot.id
            """)
    List<SpotSwipeAggregate> aggregateByGroup(@Param("groupId") UUID groupId);
}