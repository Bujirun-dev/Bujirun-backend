package com.bujirun.bujirun.domain.spot.dto.response;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class SpotDetailResponse {
    private String spotId;
    private String contentId;
    private String name;
    private String category;
    private String collectionCategory; // (바다/자연/문화/체험)
    private boolean isCollection;
    private String address;
    private BigDecimal lat;
    private BigDecimal lng;
    private String thumbnailUrl;
    private String operatingHours;
    private String overview;
    private String tel;
    private String homepage;
    private String subtitle;
    private String transportation;
    private String closedDays;
    private String feeInfo;
    private boolean isCollected;
    private boolean isVisited;

    public static SpotDetailResponse of(
            TourSpot spot,
            TourApiResponse.DetailCommonResponse.CommonItem apiDetail,
            boolean isCollected,
            boolean isVisited) {

        SpotDetailResponseBuilder builder = SpotDetailResponse.builder()
                .spotId(spot.getId().toString())
                .contentId(spot.getContentId())
                .name(spot.getName())
                .category(spot.getCategory())
                .collectionCategory(spot.getCollectionCategory())
                .isCollection(spot.isCollection())
                .address(spot.getAddress())
                .lat(spot.getLat())
                .lng(spot.getLng())
                .thumbnailUrl(spot.getThumbnailUrl())
                .operatingHours(spot.getOperatingHours())
                .isCollected(isCollected)
                .isVisited(isVisited)
                .subtitle(spot.getSubtitle())
                .transportation(defaultIfBlank(spot.getTransportation(), "등록된 정보 없음"))
                .closedDays(defaultIfBlank(spot.getClosedDays(), "등록된 정보 없음"))
                .feeInfo(defaultIfBlank(spot.getFeeInfo(), "등록된 정보 없음"))
                .overview("등록된 정보 없음")
                .tel("등록된 정보 없음")
                .homepage("등록된 정보 없음");

        // 요약본(summaryDescription)이 있으면 최우선, 없으면 description(부산명소정보 보완/TourAPI 개요),
        // 그마저 없으면 TourAPI 실시간 조회 개요 순으로 노출
        if (spot.getSummaryDescription() != null && !spot.getSummaryDescription().isBlank()) {
            builder.overview(spot.getSummaryDescription());
        } else if (spot.getDescription() != null && !spot.getDescription().isBlank()) {
            builder.overview(spot.getDescription());
        } else if (apiDetail != null) {
            builder.overview(defaultIfBlank(apiDetail.getOverview(), "등록된 정보 없음"));
        }

        if (spot.getContact() != null && !spot.getContact().isBlank()) {
            builder.tel(spot.getContact());
        } else if (apiDetail != null) {
            builder.tel(defaultIfBlank(apiDetail.getTel(), "등록된 정보 없음"));
        }

        if (spot.getHomepageUrl() != null && !spot.getHomepageUrl().isBlank()) {
            builder.homepage(spot.getHomepageUrl());
        } else if (apiDetail != null) {
            builder.homepage(defaultIfBlank(apiDetail.getHomepage(), "등록된 정보 없음"));
        }

        return builder.build();
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.isBlank()) ? defaultValue : value;
    }
}