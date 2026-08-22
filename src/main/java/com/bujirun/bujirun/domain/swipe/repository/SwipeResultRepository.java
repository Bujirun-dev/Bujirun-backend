package com.bujirun.bujirun.domain.swipe.repository;

import com.bujirun.bujirun.domain.swipe.dto.projection.SpotSwipeAggregate;
import com.bujirun.bujirun.domain.swipe.dto.projection.UserCategoryLike;
import com.bujirun.bujirun.domain.swipe.dto.projection.UserSwipeAggregate;
import com.bujirun.bujirun.domain.swipe.entity.SwipeResult;
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

    // 참여자별 selectivity(1 - 좋아요수/전체스와이프수) 계산을 위한 유저별 전체 좋아요수/전체스와이프수 집계
    // "가장 최근 completed 세션" 기준은 aggregateByGroup과 동일한 서브쿼리를 사용한다
    @Query("""
            SELECT sr.session.userId AS userId,
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
            GROUP BY sr.session.userId
            """)
    List<UserSwipeAggregate> aggregateUserStatsByGroup(@Param("groupId") UUID groupId);

    // 유저-카테고리 좋아요 쌍(중복 제거). 한 유저가 같은 카테고리를 여러 번 좋아요해도
    // categoryScore 집계 시 해당 유저의 selectivity가 1회만 반영되도록 DISTINCT로 조회한다
    // collectionCategory(도감 4분류: 바다/자연/문화/체험) 기준 — spot.category(TourAPI cat1 기반 6분류)와는 다른 필드이니 혼동 주의
    @Query("""
            SELECT DISTINCT sr.session.userId AS userId, sr.spot.collectionCategory AS category
            FROM SwipeResult sr
            WHERE sr.liked = true
              AND sr.spot.collectionCategory IS NOT NULL
              AND sr.session.id IN (
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
            """)
    List<UserCategoryLike> findLikedCategoriesByGroup(@Param("groupId") UUID groupId);
}