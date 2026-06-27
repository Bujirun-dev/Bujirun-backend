package com.bujirun.bujirun.global.util;

public final class GeoUtils {

    private static final int EARTH_RADIUS_M = 6371000;

    private GeoUtils() {}

    /**
     * 두 좌표 간 직선거리(하버사인 공식, 단위: m)
     */
    public static double haversineDistance(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
