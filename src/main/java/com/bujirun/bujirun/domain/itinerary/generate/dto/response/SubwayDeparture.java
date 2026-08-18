package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

public record SubwayDeparture(
        String departureTime, // 시각표상 출발 예정 시각 ("HH:mm") - ODsay 배차표 기준, GPS 실시간 위치 정보 아님
        int subwayClass,      // 열차 타입 (0: 일반, 1: 급행, 2: 특급)
        int firstLastFlag     // 첫차/막차 여부 (0: 일반, 1: 첫차, 2: 막차, 3: 첫차이자막차)
) {}
