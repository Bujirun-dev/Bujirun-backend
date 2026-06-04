package com.bujirun.bujirun.domain.schedule.controller;

import com.bujirun.bujirun.domain.schedule.dto.ScheduleResponse;
import com.bujirun.bujirun.domain.schedule.dto.SwipeRequest;
import com.bujirun.bujirun.domain.schedule.service.ScheduleService;
import com.bujirun.bujirun.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    /**
     * 스와이프 결과 기반 A/B/C 일정 자동 생성
     * POST /api/schedules/generate
     */
    @PostMapping("/generate")
    public ApiResponse<ScheduleResponse> generateSchedule(
            @Valid @RequestBody SwipeRequest request) {
        ScheduleResponse response = scheduleService.generateSchedule(request);
        return ApiResponse.ok(response);
    }
}