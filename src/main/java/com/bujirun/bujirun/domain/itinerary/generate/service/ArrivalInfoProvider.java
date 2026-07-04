package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SubPath;

public interface ArrivalInfoProvider {
    boolean supports(String type);
    Integer getNextArrival(SubPath subPath);
}