package com.bujirun.bujirun.domain.schedule.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SpotInfo {
    private String contentId;
    private String name;
    private String category;
    private String sigungu;
    private double lat;
    private double lng;
    private String address;
    private String thumbnailUrl;
    private String operatingHours;
}