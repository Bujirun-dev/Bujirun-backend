package com.bujirun.bujirun.domain.swipe.repository;

import com.bujirun.bujirun.domain.swipe.entity.SwipeSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface SwipeSessionRepository extends JpaRepository<SwipeSession, UUID> {
    @Query("SELECT COUNT(DISTINCT s.userId) FROM SwipeSession s " +
            "WHERE s.groupId = :groupId AND s.status = 'completed'")
    long countDistinctCompletedUsersByGroupId(@Param("groupId") UUID groupId);
}