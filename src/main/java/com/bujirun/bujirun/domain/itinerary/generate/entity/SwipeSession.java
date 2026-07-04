package com.bujirun.bujirun.domain.itinerary.generate.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "swipe_sessions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SwipeSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "group_id")
    private UUID groupId;

    // 'active' | 'completed' | 'deleted' (DB CHECK 제약)
    @Column(length = 20, nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Builder
    public SwipeSession(UUID userId, UUID groupId, String status) {
        this.userId = userId;
        this.groupId = groupId;
        this.status = status;
    }

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) {
            this.status = "active";
        }
    }
}
