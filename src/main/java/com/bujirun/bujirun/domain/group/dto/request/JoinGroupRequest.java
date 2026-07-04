package com.bujirun.bujirun.domain.group.dto.request;

import jakarta.validation.constraints.NotBlank;

public record JoinGroupRequest(
        @NotBlank String inviteCode
) {}
