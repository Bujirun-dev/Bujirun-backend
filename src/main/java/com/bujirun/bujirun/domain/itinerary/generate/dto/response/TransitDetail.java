package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * subPath 배열 전체(도보/버스/지하철 전 구간)를 보존하는 경로 상세.
 * itinerary_items.transit_detail(JSONB)에 그대로 저장된다 — route_type/route_no 등
 * 기존 대표값 컬럼(첫 대중교통 구간만 표현)의 한계를 보완하는 용도.
 */
public record TransitDetail(
        List<TransitDetailSegment> segments
) {
    public static final TransitDetail EMPTY = new TransitDetail(List.of());

    // option의 subPath 전체를 순서대로 옮기고, subwaySchedules(지하철 구간만 매핑된 결과)를
    // subPathIndex 기준으로 끼워 넣는다. leg가 없거나 subPath가 없으면 빈 상세를 반환한다.
    public static TransitDetail from(TransitOption option, List<SubwaySegmentTimetable> subwaySchedules) {
        if (option == null || option.subPaths() == null || option.subPaths().isEmpty()) return EMPTY;

        Map<Integer, SubwaySegmentTimetable> byIndex = subwaySchedules == null || subwaySchedules.isEmpty()
                ? Map.of()
                : subwaySchedules.stream()
                        .collect(Collectors.toMap(SubwaySegmentTimetable::subPathIndex, s -> s));

        List<SubPath> subPaths = option.subPaths();
        List<TransitDetailSegment> segments = new ArrayList<>();
        for (int i = 0; i < subPaths.size(); i++) {
            segments.add(TransitDetailSegment.from(i, subPaths.get(i), byIndex.get(i)));
        }
        return new TransitDetail(segments);
    }
}
