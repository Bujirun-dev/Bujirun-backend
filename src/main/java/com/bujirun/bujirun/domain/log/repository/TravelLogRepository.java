package com.bujirun.bujirun.domain.log.repository;

import com.bujirun.bujirun.domain.log.entity.TravelLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TravelLogRepository extends JpaRepository<TravelLog, UUID> {
    boolean existsByItineraryIdAndUserId(UUID itineraryId, UUID userId);
    List<TravelLog> findByItineraryIdInAndUserId(Collection<UUID> itineraryIds, UUID userId);
    long countByUserId(UUID userId);
    List<TravelLog> findByUserIdOrderByCreatedAtDesc(UUID userId);
    List<TravelLog> findByIsPublicTrueOrderByCreatedAtDesc();
    List<TravelLog> findByIsPublicTrueOrderByAddedCountDesc();
    List<TravelLog> findByIdInAndIsPublicTrueOrderByCreatedAtDesc(Collection<UUID> ids);
    List<TravelLog> findByIdInAndIsPublicTrueOrderByAddedCountDesc(Collection<UUID> ids);

    // 회원탈퇴 30일 경과 후 여행 기록 완전 삭제용 — travel_log_items/photos/hashtags는
    // DB의 ON DELETE CASCADE(travel_logs FK)로 함께 삭제된다. 이티너리(여행 일정)는
    // travel_logs가 참조만 할 뿐(itinerary_id는 단순 컬럼) 소유하지 않으므로 영향 없음
    @Modifying
    @Query("delete from TravelLog t where t.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);
}
