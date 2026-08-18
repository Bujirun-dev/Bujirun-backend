package com.bujirun.bujirun.domain.itinerary.generate.dto.response;

import java.util.List;

public record SubwayTransitInfo(
        int count,
        List<SubwayTransferDetail> transitTotalInfo
) {}
