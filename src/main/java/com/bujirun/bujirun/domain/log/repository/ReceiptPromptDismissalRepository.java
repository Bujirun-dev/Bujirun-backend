package com.bujirun.bujirun.domain.log.repository;

import com.bujirun.bujirun.domain.log.entity.ReceiptPromptDismissal;
import com.bujirun.bujirun.domain.log.entity.ReceiptPromptDismissalId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReceiptPromptDismissalRepository
        extends JpaRepository<ReceiptPromptDismissal, ReceiptPromptDismissalId> {

    boolean existsByUserIdAndItineraryId(UUID userId, UUID itineraryId);

    void deleteByUserIdAndItineraryId(UUID userId, UUID itineraryId);

    // 배치 조회: 주어진 일정들 중 이 사용자가 "다시 묻지 않음" 처리한 일정 id만 반환
    @Query("select d.itineraryId from ReceiptPromptDismissal d "
            + "where d.userId = :userId and d.itineraryId in :itineraryIds")
    List<UUID> findDismissedItineraryIds(@Param("userId") UUID userId,
                                        @Param("itineraryIds") Collection<UUID> itineraryIds);

    // 회원탈퇴 30일 경과 후 정리 — travel_logs와 함께 삭제
    @Modifying
    @Query("delete from ReceiptPromptDismissal d where d.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
