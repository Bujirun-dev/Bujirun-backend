package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.itinerary.generate.service.BusArrivalService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대중교통", description = "버스 실시간 도착정보 조회 API")
@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class BusArrivalController {

    private final BusArrivalService busArrivalService;

    @Operation(summary = "버스 실시간 도착정보 조회", description = "정류소 ID와 노선번호로 버스의 실시간 도착 예정시간(분)을 조회합니다. 프론트엔드 폴링용 API입니다.")
    @GetMapping("/arrival/bus")
    public ApiResponse<Integer> getBusArrival(
            @RequestParam String arsId,
            @RequestParam String routeNo) {
        Integer remainMinutes = busArrivalService.getArrivalByArsId(arsId, routeNo);
        return ApiResponse.ok(remainMinutes);
    }
}
