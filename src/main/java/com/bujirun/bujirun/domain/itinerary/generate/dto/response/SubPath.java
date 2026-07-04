package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

public record SubPath(
        String type,         // 구간 타입 (도보/버스/지하철)
        int sectionTime,      // 구간 소요 시간(분)
        String routeNo,       // 노선 번호/이름 (버스 번호, 지하철 노선명 등. 도보는 "")
        int stationCount,     // 경유 정류장(역) 개수. 도보는 0
        String startName,     // 승차 정류장명 (버스: 정류장명, 지하철: 역명, 도보: "")
        String endName,       // 하차 정류장명 (버스: 정류장명, 지하철: 역명, 도보: "")
        double startX,        // 승차 정류장 경도
        double startY,        // 승차 정류장 위도
        double endX,          // 하차 정류장 경도
        double endY,           // 하차 정류장 위도
        String startArsId,     // 버스 승차 정류장 ARS 번호 (마을버스 등 일부 없음 → "", 지하철/도보: "")
        int startId,           // 지하철 승차역 코드 - ODsay startID (버스/도보: 0)
        int wayCode,           // 지하철 방향 - ODsay wayCode (1=상행, 2=하행, 버스/도보: 0)
        Integer remainMinutes  // 다음 버스/열차까지 대기 시간 (분) - 조회 실패 또는 도보 시 null
) {}