package com.bujirun.bujirun.domain.visit.repository;

import com.bujirun.bujirun.domain.visit.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    List<Visit> findByUserIdOrderByVisitedAtDesc(UUID userId);

    boolean existsByUserIdAndSpotIdAndVerifiedTrue(UUID userId, UUID spotId);

    // 관광지 목록/일정 조회 시 스팟별 인증 여부를 배치로 매핑하기 위한 spotId 집합 조회
    @Query("select distinct v.spot.id from Visit v where v.userId = :userId and v.verified = true")
    List<UUID> findVerifiedSpotIdsByUserId(@Param("userId") UUID userId);
}
