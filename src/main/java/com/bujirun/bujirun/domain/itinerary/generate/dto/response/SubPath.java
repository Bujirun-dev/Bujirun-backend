package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

public record SubPath(
        String type,         // 구간 타입 (도보/버스/지하철)
        int sectionTime,      // 구간 소요 시간(분)
        String routeNo,       // 노선 번호/이름 (버스 번호, 지하철 노선명 등. 도보는 "")
        int stationCount,     // 경유 정류장(역) 개수. 도보는 0
        String startName,     // 승차 정류장명
        String endName,       // 하차 정류장명
        double startX,        // 승차 정류장 경도
        double startY,        // 승차 정류장 위도
        double endX,          // 하차 정류장 경도
        double endY           // 하차 정류장 위도
) {}