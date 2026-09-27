package com.bujirun.bujirun.domain.itinerary.repository;

import com.bujirun.bujirun.domain.itinerary.entity.ItineraryDay;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
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

    // day의 @Version은 day 엔티티 자신의 컬럼이 바뀔 때만 Hibernate가 자동으로 올린다.
    // replaceDayItems/reorderItems는 day에 속한 item(자식 테이블 row)만 갱신해서 dirty
    // checking이 day 자체는 "안 바뀜"으로 보고 UPDATE 자체를 안 낸다 — entityManager.lock
    // (OPTIMISTIC_FORCE_INCREMENT)으로 시도했지만 실제로 아무 SQL도 나가지 않는 걸 로컬에서
    // 확인(2026-09-17, show-sql로 검증). JPQL bulk update로 명시적으로 올린다. 이미
    // findByIdForUpdate로 행 잠금을 쥔 상태에서만 호출하므로 동시성 문제는 없다.
    @Modifying
    @Query("update ItineraryDay d set d.version = d.version + 1 where d.id = :id")
    void bumpVersion(@Param("id") UUID id);
}
