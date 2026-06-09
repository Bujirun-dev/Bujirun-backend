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

    @PrePersist
    void prePersist() {
        if (visitedAt == null) visitedAt = LocalDateTime.now();
    }
}
