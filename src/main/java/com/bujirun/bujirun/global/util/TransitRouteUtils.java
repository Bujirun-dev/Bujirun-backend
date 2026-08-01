package com.bujirun.bujirun.global.util;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;

import java.util.List;

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
}