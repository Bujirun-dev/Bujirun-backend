package com.bujirun.bujirun.domain.group.dto.request;

import jakarta.validation.constraints.Size;

public record CreateGroupRequest(
        @Size(max = 100) String name
) {}
