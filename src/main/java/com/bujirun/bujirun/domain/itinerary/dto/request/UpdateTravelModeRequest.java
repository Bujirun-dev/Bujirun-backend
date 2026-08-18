package com.bujirun.bujirun.domain.itinerary.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record UpdateTravelModeRequest(
        @NotBlank
        // bus(버스 전용)/subway(지하철 전용)/combo(버스+지하철 조합)는 ODsay가 pathType별로 계산해준
        // 별도 옵션을 그대로 선택할 때 쓴다. transit은 하위호환용 — 셋 중 가장 빠른 옵션이 선택된다.
        @Pattern(regexp = "walk|transit|taxi|bus|subway|combo",
                message = "travelMode은 walk, transit, taxi, bus, subway, combo 중 하나여야 합니다.")
        String travelMode
) {}
