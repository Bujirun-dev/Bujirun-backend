package com.bujirun.bujirun.domain.spot.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tour_spots")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TourSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "content_id", nullable = false, unique = true)
    private String contentId;

    @Column(nullable = false)
    private String name;

    private String category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sigungu_id")
    private Sigungu sigungu;

    @Column(precision = 10, scale = 7)
    private BigDecimal lat;

    @Column(precision = 10, scale = 7)
    private BigDecimal lng;

    private String address;

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "operating_hours")
    private String operatingHours;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @Column(name = "is_collection", nullable = false)
    @Builder.Default
    private boolean collection = false;

    @PrePersist
    public void prePersist() {
        this.syncedAt = LocalDateTime.now();
    }

    public void update(String name, String category, Sigungu sigungu,
                       BigDecimal lat, BigDecimal lng, String address,
                       String thumbnailUrl, String operatingHours) {
        this.name           = name;
        this.category       = category;
        this.sigungu        = sigungu;
        this.lat            = lat;
        this.lng            = lng;
        this.address        = address;
        this.thumbnailUrl   = thumbnailUrl;
        this.operatingHours = operatingHours;
        this.syncedAt       = LocalDateTime.now();
    }
}