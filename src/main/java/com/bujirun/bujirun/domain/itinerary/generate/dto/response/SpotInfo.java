package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
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
    private List<String> reasons; // 추가: 그룹 일정 생성 시에만 채워지는 AI 추천 이유 (개인 일정은 항상 null)
}