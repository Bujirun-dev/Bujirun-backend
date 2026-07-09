package com.bujirun.bujirun.domain.itinerary.generate.service;

import com.bujirun.bujirun.domain.itinerary.generate.dto.response.SpotInfo;
import com.bujirun.bujirun.global.util.GeoUtils;

import java.util.ArrayList;
import java.util.List;

public final class SpotOrderOptimizer {

    private SpotOrderOptimizer() {}

    public static List<SpotInfo> sortByNearestNeighbor(List<SpotInfo> spots) {
        if (spots.size() <= 2) return new ArrayList<>(spots);

        List<SpotInfo> remaining = new ArrayList<>(spots);
        List<SpotInfo> sorted = new ArrayList<>();

        SpotInfo current = remaining.remove(0);
        sorted.add(current);

        while (!remaining.isEmpty()) {
            SpotInfo nearest = null;
            double minDist = Double.MAX_VALUE;

            for (SpotInfo candidate : remaining) {
                double dist = GeoUtils.haversineDistance(
                        current.getLat(), current.getLng(),
                        candidate.getLat(), candidate.getLng()
                );
                if (dist < minDist) {
                    minDist = dist;
                    nearest = candidate;
                }
            }

            sorted.add(nearest);
            remaining.remove(nearest);
            current = nearest;
        }

        return sorted;
    }
}

