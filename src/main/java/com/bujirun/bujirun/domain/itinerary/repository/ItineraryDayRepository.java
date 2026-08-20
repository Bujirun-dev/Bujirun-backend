package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ItineraryDayRepository extends JpaRepository<ItineraryDay, UUID> {

    boolean existsByItineraryIdAndDayNumber(UUID itineraryId, int dayNumber);

    // day당 최대 관광지 개수(MAX_ITEMS_PER_DAY) 체크와 삽입을 같은 트랜잭션에서 원자적으로
    // 만들기 위한 행 잠금 조회. 일반 findById로는 "개수 확인 → 삽입" 사이에 다른 트랜잭션이
    // 끼어들 수 있어(check-then-act 레이스), 특히 실시간 협업 편집이 이탈/합류 시 여러 항목을
    // Promise.allSettled로 동시에 addItem 호출하는 상황([[flushDayToRest]])에서 정원을
    // 넘겨 저장되는 문제가 실제로 재현됨 — 같은 day에 대한 addItem을 이 잠금으로 직렬화한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ItineraryDay d where d.id = :id")
    Optional<ItineraryDay> findByIdForUpdate(@Param("id") UUID id);
}
