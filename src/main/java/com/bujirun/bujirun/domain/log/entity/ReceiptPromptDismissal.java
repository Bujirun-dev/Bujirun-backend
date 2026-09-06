package com.bujirun.bujirun.domain.log.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 영수증 발행(여행 기록 작성) 유도 팝업에서 "다시 묻지 않음"을 선택한 (사용자, 일정) 조합.
 * 이 행이 존재하면 해당 일정에 대해서는 영수증 발행 팝업을 다시 띄우지 않는다.
 * itinerary_id는 travel_logs와 마찬가지로 참조만 하며 일정을 소유하지 않는다(일정 삭제 시 함께 삭제).
 */
@Entity
@Table(name = "receipt_prompt_dismissals")
@IdClass(ReceiptPromptDismissalId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReceiptPromptDismissal {

    @Id
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Id
    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private ReceiptPromptDismissal(UUID userId, UUID itineraryId) {
        this.userId = userId;
        this.itineraryId = itineraryId;
        this.createdAt = LocalDateTime.now();
    }
}
