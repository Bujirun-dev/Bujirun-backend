package com.bujirun.bujirun.domain.visit.repository;

import com.bujirun.bujirun.domain.visit.entity.Visit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VisitRepository extends JpaRepository<Visit, UUID> {

    List<Visit> findByUserIdOrderByVisitedAtDesc(UUID userId);

    boolean existsByUserIdAndSpotIdAndVerifiedTrue(UUID userId, UUID spotId);
}
