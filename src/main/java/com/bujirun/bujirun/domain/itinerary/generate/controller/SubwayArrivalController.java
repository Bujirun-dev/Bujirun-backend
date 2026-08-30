package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.itinerary.generate.service.SubwayArrivalService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대중교통", description = "지하철 도착정보 조회 API")
@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class SubwayArrivalController {

    private final SubwayArrivalService subwayArrivalService;

    @Operation(summary = "지하철 도착정보 조회", description = "역 코드와 방향(상행/하행)으로 다음 지하철까지 남은 시간(분)을 조회합니다. ODsay 배차 시각표 기준 추정치이며 실시간 위치 추적 결과가 아닙니다. 프론트엔드 폴링용 API입니다.")
    @GetMapping("/arrival/subway")
    public ApiResponse<Integer> getSubwayArrival(
            @RequestParam int stationId,
            @RequestParam int wayCode) {
        Integer remainMinutes = subwayArrivalService.getArrivalByStationId(stationId, wayCode);
        return ApiResponse.ok(remainMinutes);
    }
}
