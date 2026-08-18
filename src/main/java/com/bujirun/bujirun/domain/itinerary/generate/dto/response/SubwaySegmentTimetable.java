package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import java.util.List;

/**
 * 대중교통 길찾기 결과(subPath) 중 지하철 구간 하나에 ODsay 배차 시각표/환승 정보를 매핑한 결과.
 * 전부 "예정"(시각표 기준) 정보이며 GPS 기반 실시간 도착정보가 아니다 — 프론트 노출 시에도 "실시간" 대신
 * "예정" 또는 "시각표 기준"으로 표기할 것.
 */
public record SubwaySegmentTimetable(
        int subPathIndex,                          // TransitOption.subPaths() 내 인덱스 (환승 순서 구분용)
        String startStationName,
        String endStationName,
        String lineName,                            // routeNo (호선명)
        int wayCode,
        List<SubwayDeparture> upcomingDepartures,   // 시각표 기준 다음 열차 목록 (예정, 최대 N개)
        Integer nextDepartureMinutes,                // 시각표 기준 다음 열차까지 예정 대기시간(분). 실시간 아님.
        SubwayTransitInfo transferInfo               // 직전 구간도 지하철이었던(환승 재승차) 구간에서만 채워짐. 아니면 null.
) {}
