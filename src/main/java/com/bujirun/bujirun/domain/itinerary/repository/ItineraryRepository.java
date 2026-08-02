package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItineraryRepository extends JpaRepository<Itinerary, UUID> {

    List<Itinerary> findByUserIdOrderByCreatedAtDesc(UUID userId);

    // 개인 일정만(그룹 일정은 그룹 멤버십 기준으로 별도 조회) — 그룹을 나간 뒤에도 옛 소유자 목록에 남지 않도록
    List<Itinerary> findByUserIdAndGroupIdIsNullOrderByCreatedAtDesc(UUID userId);

    List<Itinerary> findByGroupIdInOrderByCreatedAtDesc(List<UUID> groupIds);

    List<Itinerary> findByGroupId(UUID groupId);

    boolean existsByGroupId(UUID groupId);
}
