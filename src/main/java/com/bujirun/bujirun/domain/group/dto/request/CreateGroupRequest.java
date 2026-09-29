package com.bujirun.bujirun.domain.group.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @Size(max = 100) String name,
        @Min(2) @Max(6) Integer maxMembers
) {}
