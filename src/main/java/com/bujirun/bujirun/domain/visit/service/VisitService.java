package com.bujirun.bujirun.domain.visit.service;

import com.bujirun.bujirun.domain.collection.service.CollectionService;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.itinerary.entity.Itinerary;
import com.bujirun.bujirun.domain.itinerary.entity.ItineraryItem;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryItemRepository;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.spot.repository.TourSpotRepository;
import com.bujirun.bujirun.domain.visit.dto.request.AttachVisitPhotoRequest;
import com.bujirun.bujirun.domain.visit.dto.request.VisitRequest;
import com.bujirun.bujirun.domain.visit.dto.response.VisitHistoryResponse;
import com.bujirun.bujirun.domain.visit.dto.response.VisitPhotoResponse;
import com.bujirun.bujirun.domain.visit.dto.response.VisitResponse;
import com.bujirun.bujirun.domain.visit.entity.Visit;
import com.bujirun.bujirun.domain.visit.entity.VisitPhoto;
import com.bujirun.bujirun.domain.visit.repository.VisitPhotoRepository;
import com.bujirun.bujirun.domain.visit.repository.VisitRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VisitService {

    private static final double RADIUS_DEFAULT  = 100.0;
    private static final double RADIUS_OUTDOOR  = 200.0;
    private static final double RADIUS_WIDE     = 500.0;

    private final VisitRepository visitRepository;
    private final VisitPhotoRepository visitPhotoRepository;
    private final TourSpotRepository tourSpotRepository;
    private final CollectionService collectionService;
    private final ItineraryItemRepository itineraryItemRepository;
    private final GroupMemberRepository groupMemberRepository;

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

        // 이전에 이 관광지를 인증에 성공한 적이 있는지 (모달에서 "새 관광지" vs "중복" 구분용)
        boolean alreadyVerified = visitRepository.existsByUserIdAndSpotIdAndVerifiedTrue(userId, spot.getId());

        if (req.itineraryItemId() != null) {
            validateItineraryItemForSpot(req.itineraryItemId(), spot, userId);
        }

        Visit visit = Visit.builder()
                .userId(userId)
                .spot(spot)
                .gpsLat(BigDecimal.valueOf(req.gpsLat()))
                .gpsLng(BigDecimal.valueOf(req.gpsLng()))
                .verified(verified)
                .distanceMeters(distance)
                .itineraryItemId(req.itineraryItemId())
                .build();

        Visit saved = visitRepository.save(visit);

        // 방문 시 도감에 해당 관광지 저장
        if (verified && spot.isCollection()) {
            collectionService.markCollected(userId, spot.getId());
        }

        return VisitResponse.from(saved, verified && !alreadyVerified);
    }

    // 사진 촬영 후 두 번째 "인증하기"에서 호출 — GPS 인증이 완료된 방문 기록에 사진을 첨부
    @Transactional
    public VisitPhotoResponse attachPhoto(UUID visitId, AttachVisitPhotoRequest req, UUID userId) {
        Visit visit = visitRepository.findById(visitId)
                .orElseThrow(() -> new EntityNotFoundException("방문 기록을 찾을 수 없습니다. id=" + visitId));

        if (!visit.getUserId().equals(userId)) {
            throw new IllegalArgumentException("본인의 방문 기록에만 사진을 첨부할 수 있습니다.");
        }
        if (!visit.isVerified()) {
            throw new IllegalArgumentException("인증에 성공한 방문 기록에만 사진을 첨부할 수 있습니다.");
        }

        VisitPhoto photo = VisitPhoto.builder()
                .visit(visit)
                .photoUrl(req.photoUrl())
                .build();

        return VisitPhotoResponse.from(visitPhotoRepository.save(photo));
    }

    @Transactional(readOnly = true)
    public List<VisitHistoryResponse> getHistory(UUID userId) {
        List<Visit> visits = visitRepository.findByUserIdOrderByVisitedAtDesc(userId);

        Map<UUID, List<String>> photoUrlsByVisitId = visitPhotoRepository
                .findByVisitIdIn(visits.stream().map(Visit::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(
                        p -> p.getVisit().getId(),
                        Collectors.mapping(VisitPhoto::getPhotoUrl, Collectors.toList())));

        return visits.stream()
                .map(v -> VisitHistoryResponse.from(v, photoUrlsByVisitId.getOrDefault(v.getId(), List.of())))
                .toList();
    }

    // itineraryItemId로 인증 시, 그 항목의 스팟이 실제 인증 대상 스팟과 같고
    // 이 유저가 그 일정에 접근 권한(소유자 또는 그룹원)이 있는지 확인
    private void validateItineraryItemForSpot(UUID itineraryItemId, TourSpot spot, UUID userId) {
        ItineraryItem item = itineraryItemRepository.findById(itineraryItemId)
                .orElseThrow(() -> new EntityNotFoundException("일정 항목을 찾을 수 없습니다. id=" + itineraryItemId));

        if (!item.getSpot().getId().equals(spot.getId())) {
            throw new IllegalArgumentException("일정 항목의 관광지와 인증하려는 관광지가 다릅니다.");
        }

        Itinerary itinerary = item.getDay().getItinerary();
        boolean hasAccess = itinerary.getUserId().equals(userId)
                || (itinerary.getGroupId() != null
                    && groupMemberRepository.existsById_GroupIdAndId_UserId(itinerary.getGroupId(), userId));
        if (!hasAccess) {
            throw new IllegalArgumentException("해당 일정에 대한 권한이 없습니다.");
        }
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
