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

    @Column(name = "content_type_id")
    private Integer contentTypeId;

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

    @Column(name = "collection_category")
    private String collectionCategory;

    // 스와이프 덱(/api/collections/swipe-deck) 전용 큐레이션 이미지. thumbnail_url(API 동기화 값)과 별개로 관리됨
    @Column(name = "swipe_image_url", length = 500)
    private String swipeImageUrl;

    // ── 부산광역시_부산명소정보 API(data.go.kr 15063481) 보완 정보 ──
    private String subtitle;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String contact;

    @Column(name = "homepage_url")
    private String homepageUrl;

    private String transportation;

    @Column(name = "closed_days")
    private String closedDays;

    @Column(name = "fee_info")
    private String feeInfo;

    // 부산명소정보 API의 콘텐츠ID(UC_SEQ). 좌표 매칭 후 재동기화 시 바로 찾기 위한 용도
    @Column(name = "busan_uc_seq")
    private String busanUcSeq;

    @PrePersist
    public void prePersist() {
        this.syncedAt = LocalDateTime.now();
    }

    public void update(String name, String category, Sigungu sigungu,
                       BigDecimal lat, BigDecimal lng, String address,
                       String thumbnailUrl, String operatingHours,
                       Integer contentTypeId) {
        this.name           = name;
        this.category       = category;
        this.sigungu        = sigungu;
        this.lat            = lat;
        this.lng            = lng;
        this.address        = address;
        this.thumbnailUrl   = thumbnailUrl;
        this.operatingHours = operatingHours;
        this.contentTypeId  = contentTypeId;
        this.syncedAt       = LocalDateTime.now();
    }

    // 부산명소정보 API로 소개정보 보완. operatingHours는 TourAPI 값이 이미 있으면 덮어쓰지 않음
    public void enrichFromBusanAttraction(String busanUcSeq, String subtitle, String description,
                                           String contact, String homepageUrl, String transportation,
                                           String operatingHoursIfBlank, String closedDays, String feeInfo) {
        this.busanUcSeq      = busanUcSeq;
        this.subtitle        = subtitle;
        this.description     = description;
        this.contact         = contact;
        this.homepageUrl     = homepageUrl;
        this.transportation  = transportation;
        this.closedDays      = closedDays;
        this.feeInfo         = feeInfo;
        if (this.operatingHours == null || this.operatingHours.isBlank()) {
            this.operatingHours = operatingHoursIfBlank;
        }
    }
}