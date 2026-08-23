package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

/**
 * TransitOption.subPaths() 한 구간을 그대로 보존한 것.
 * trafficType=="지하철"일 때만 subwaySchedule이 채워지며, 이는 ODsay 배차 시각표 기준
 * "예정" 정보이지 GPS 기반 실시간 도착정보가 아니다.
 */
public record TransitDetailSegment(
        int index,
        String trafficType,   // "도보"/"버스"/"지하철" (SubPath.type() 그대로)
        String startName,
        String endName,
        String routeNo,
        int sectionTime,
        // 버스 실시간 도착정보 폴링(GET /api/transit/arrival/bus)에 필요한 arsId.
        // SubPath.startArsId()를 그대로 옮긴 것 — 버스 구간에만 값이 있고, 지하철/도보/마을버스는 빈 문자열일 수 있음
        String startArsId,
        SubwaySegmentTimetable subwaySchedule // trafficType=="지하철"일 때만 non-null
) {
    public static TransitDetailSegment from(int index, SubPath subPath, SubwaySegmentTimetable subwaySchedule) {
        return new TransitDetailSegment(
                index,
                subPath.type(),
                subPath.startName(),
                subPath.endName(),
                subPath.routeNo(),
                subPath.sectionTime(),
                subPath.startArsId(),
                subwaySchedule
        );
    }
}
