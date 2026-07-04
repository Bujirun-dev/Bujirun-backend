package com.bujirun.bujirun.domain.visit.service;

import com.bujirun.bujirun.domain.collection.service.CollectionService;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.visit.dto.request.VisitRequest;
import com.bujirun.bujirun.domain.visit.dto.response.VisitResponse;
import com.bujirun.bujirun.domain.visit.entity.Visit;
import com.bujirun.bujirun.domain.visit.repository.VisitRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class VisitService {

    private static final double RADIUS_DEFAULT  = 100.0;
    private static final double RADIUS_OUTDOOR  = 200.0;
    private static final double RADIUS_WIDE     = 500.0;

    private final VisitRepository visitRepository;
    private final TourSpotRepository tourSpotRepository;
    private final CollectionService collectionService;

    @Transactional
    public VisitResponse verify(VisitRequest req, UUID userId) {
        TourSpot spot = tourSpotRepository.findById(req.tourSpotId())
                .orElseThrow(() -> new EntityNotFoundException("관광지를 찾을 수 없습니다. id=" + req.tourSpotId()));

        if (spot.getLat() == null || spot.getLng() == null) {
            throw new IllegalArgumentException("관광지 좌표 정보가 없습니다. id=" + req.tourSpotId());
        }

        double distance = haversine(
                req.gpsLat(), req.gpsLng(),
                spot.getLat().doubleValue(), spot.getLng().doubleValue()
        );

        double radius = radiusFor(spot.getCategory());
        boolean verified = distance <= radius;

        Visit visit = Visit.builder()
                .userId(userId)
                .spot(spot)
                .gpsLat(BigDecimal.valueOf(req.gpsLat()))
                .gpsLng(BigDecimal.valueOf(req.gpsLng()))
                .verified(verified)
                .distanceMeters(distance)
                .build();

        Visit saved = visitRepository.save(visit);

        // 방문 시 도감에 해당 관광지 저장
        if (verified && spot.isCollection()) {
            collectionService.markCollected(userId, spot.getId());
        }

        return VisitResponse.from(visitRepository.save(visit));
    }

    private double radiusFor(String category) {
        if (category == null) return RADIUS_DEFAULT;
        return switch (category) {
            case "자연·공원" -> RADIUS_WIDE;
            case "체험·놀이" -> RADIUS_OUTDOOR;
            default         -> RADIUS_DEFAULT;
        };
    }

    // Haversine 공식 — 두 좌표 간 거리(미터)
    private double haversine(double lat1, double lng1, double lat2, double lng2) {
        final double R = 6_371_000.0; // 지구 반지름(미터)
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
