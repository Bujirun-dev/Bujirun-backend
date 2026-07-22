package com.bujirun.bujirun.domain.visit.entity;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "visits")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private TourSpot spot;

    @Column(name = "gps_lat", nullable = false, precision = 10, scale = 7)
    private BigDecimal gpsLat;

    @Column(name = "gps_lng", nullable = false, precision = 10, scale = 7)
    private BigDecimal gpsLng;

    @Column(nullable = false)
    private boolean verified;

    @Column(name = "distance_meters", nullable = false)
    private double distanceMeters;

    @Column(name = "visited_at", nullable = false)
    private LocalDateTime visitedAt;

    // 이 인증이 어느 일정의 어느 방문 항목에 대한 것인지 (선택) — 같은 스팟을 여러 일정에서
    // 각각 인증했을 때, 특정 일정의 인증만 구분해서 쓰기 위함. 일정 항목이 삭제되면 null이 됨.
    @Column(name = "itinerary_item_id")
    private UUID itineraryItemId;

    @PrePersist
    void prePersist() {
        if (visitedAt == null) visitedAt = LocalDateTime.now();
    }
}
