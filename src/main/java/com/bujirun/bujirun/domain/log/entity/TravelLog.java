package com.bujirun.bujirun.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "travel_logs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TravelLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "itinerary_id", nullable = false)
    private UUID itineraryId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "is_public", nullable = false)
    @Builder.Default
    private boolean isPublic = false;

    @Column(name = "thumbnail_photo_url")
    private String thumbnailPhotoUrl;

    // 영수증 발행 화면의 이모티콘 선택 인덱스 — 매핑표는 프론트에서 관리, 백엔드는 값만 보관
    @Column(name = "mood")
    private Integer mood;

    @Column(name = "theme")
    private String theme;

    @Column(name = "added_count", nullable = false)
    @Builder.Default
    private int addedCount = 0;

    // 작성자 기준 몇 번째 여행 기록인지 — 생성 시점에 확정되어 이후 로그 삭제와 무관하게 유지됨
    @Column(name = "travel_number", nullable = false)
    private int travelNumber;

    @Builder.Default
    @OneToMany(mappedBy = "travelLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    private List<TravelLogItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void updateVisibility(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public void updateThumbnail(String photoUrl) {
        this.thumbnailPhotoUrl = photoUrl;
    }

    public void updateReview(Integer mood, String theme) {
        this.mood = mood;
        this.theme = theme;
    }

    public void incrementAddedCount() {
        this.addedCount++;
    }
}
