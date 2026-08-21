package com.bujirun.bujirun.domain.visit.repository;

import com.bujirun.bujirun.domain.visit.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    List<Visit> findByUserIdOrderByVisitedAtDesc(UUID userId);

    boolean existsByUserIdAndSpotIdAndVerifiedTrue(UUID userId, UUID spotId);

    // 관광지 목록/일정 조회 시 스팟별 인증 여부를 배치로 매핑하기 위한 spotId 집합 조회
    @Query("select distinct v.spot.id from Visit v where v.userId = :userId and v.verified = true")
    List<UUID> findVerifiedSpotIdsByUserId(@Param("userId") UUID userId);

    // 여행 기록 생성 시 일정 항목별 인증 기록을 매칭하기 위한 조회 (itineraryItemId로 직접 연결된 경우)
    // 같은 항목에 대해 재인증 등으로 여러 건이 쌓였을 수 있어 최신순으로 정렬 — 호출부에서 첫 건만 사용
    List<Visit> findByUserIdAndItineraryItemIdInAndVerifiedTrueOrderByVisitedAtDesc(UUID userId, Collection<UUID> itineraryItemIds);

    // 인증 시 itineraryItemId를 넘기지 않은 경우를 대비한 스팟 기준 폴백 조회
    // 같은 스팟을 여러 번(다른 일정에서도) 방문 인증했을 수 있어 최신순으로 정렬 — 호출부에서 첫 건만 사용
    List<Visit> findByUserIdAndSpotIdInAndVerifiedTrueOrderByVisitedAtDesc(UUID userId, Collection<UUID> spotIds);

    // 관광지 추천순 정렬용 — 사용자가 방문 인증을 많이 한 순서로 카테고리 조회 (1위가 필요하면 첫 건만 사용)
    @Query("""
            select v.spot.category
            from Visit v
            where v.userId = :userId and v.verified = true and v.spot.category is not null
            group by v.spot.category
            order by count(v) desc
            """)
    List<String> findMostVisitedCategories(@Param("userId") UUID userId);
}
