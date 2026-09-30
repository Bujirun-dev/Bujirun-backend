package com.bujirun.bujirun.global.util;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;
import com.bujirun.bujirun.domain.itinerary.generate.dto.response.TransitOption;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransitRouteUtils {

    // 도보 구간을 건너뛰고 실제 대중교통(버스/지하철) 구간을 찾음
    // 전 구간이 도보뿐이면(초근거리 이동) null 반환
    public static SubPath findFirstTransitSubPath(List<SubPath> subPaths) {
        if (subPaths == null || subPaths.isEmpty()) return null;
        return subPaths.stream()
                .filter(sp -> !"도보".equals(sp.type()))
                .findFirst()
                .orElse(null);
    }

    // trafficType=1(지하철) 구간 전부를 (원본 subPaths 내 인덱스, SubPath) 페어로 반환.
    // 환승이 2회 이상이어서 지하철 구간이 여러 개인 경로도 전부 순회할 수 있게 인덱스를 함께 준다.
    public static List<Map.Entry<Integer, SubPath>> findSubwaySubPaths(List<SubPath> subPaths) {
        if (subPaths == null || subPaths.isEmpty()) return List.of();
        List<Map.Entry<Integer, SubPath>> result = new ArrayList<>();
        for (int i = 0; i < subPaths.size(); i++) {
            if ("지하철".equals(subPaths.get(i).type())) {
                result.add(Map.entry(i, subPaths.get(i)));
            }
        }
        return result;
    }

    // 선택된 구간(leg)의 요금을 itinerary_items.travel_fare에 저장할 값으로 변환한다.
    // 저장 경로(투표 확정/일정 추가/일정 교체/이동수단 변경/최적화)가 모두 이 메서드를 거친다.
    // 구간이 없으면(첫 항목, 경로 조회 실패) 요금을 알 수 없으므로 null
    public static Integer toTravelFare(TransitOption leg) {
        return leg != null ? leg.totalFare() : null;
    }

    // ODsay/자체계산 TransitOption.type()의 한글 값을 DB travel_mode 허용값(walk/transit/taxi)으로 변환
    public static String toTravelMode(String type) {
        return switch (type) {
            case "도보" -> "walk";
            case "택시" -> "taxi";
            default -> "transit"; // "대중교통" 등
        };
    }
}