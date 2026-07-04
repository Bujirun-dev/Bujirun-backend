package com.bujirun.bujirun.domain.itinerary.generate.controller;

import com.bujirun.bujirun.domain.itinerary.generate.service.BusArrivalService;
import com.bujirun.bujirun.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit")
@RequiredArgsConstructor
public class BusArrivalController {

    private final BusArrivalService busArrivalService;

    /**
     * 버스 실시간 대기시간 조회 (프론트 폴링용)
     * GET /api/transit/arrival/bus?arsId=09727&routeNo=38
     */
    @GetMapping("/arrival/bus")
    public ApiResponse<Integer> getBusArrival(
            @RequestParam String arsId,
            @RequestParam String routeNo) {
        Integer remainMinutes = busArrivalService.getArrivalByArsId(arsId, routeNo);
        return ApiResponse.ok(remainMinutes);
    }
}
