package com.bujirun.bujirun.domain.itinerary.vote.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
public class CastVoteRequest {

    @NotBlank
    @Pattern(regexp = "[ABC]", message = "votedPlan은 A, B, C 중 하나여야 합니다.")
    private String votedPlan;
}