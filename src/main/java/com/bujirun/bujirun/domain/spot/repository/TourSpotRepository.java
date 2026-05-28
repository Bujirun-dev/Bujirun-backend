package com.bujirun.bujirun.domain.spot.repository;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TourSpotRepository extends JpaRepository<TourSpot, UUID> {

    Optional<TourSpot> findByContentId(String contentId);

    List<TourSpot> findBySigunguId(Integer sigunguId);

    List<TourSpot> findByCategory(String category);

    // 반경 N km 이내 관광지
    @Query(value = """
            SELECT *, (
                6371 * acos(
                    cos(radians(:lat)) * cos(radians(lat))
                    * cos(radians(lng) - radians(:lng))
                    + sin(radians(:lat)) * sin(radians(lat))
                )
            ) AS distance
            FROM tour_spots
            HAVING distance < :radiusKm
            ORDER BY distance
            """, nativeQuery = true)
    List<TourSpot> findNearby(@Param("lat") double lat,
                              @Param("lng") double lng,
                              @Param("radiusKm") double radiusKm);

    // 미수집 관광지 우선 노출
    @Query(value = """
            SELECT ts.* FROM tour_spots ts
            WHERE ts.id NOT IN (
                SELECT spot_id FROM collection_entries
                WHERE user_id = CAST(:userId AS uuid)
                AND collected = true
            )
            ORDER BY RANDOM()
            LIMIT :limit
            """, nativeQuery = true)
    List<TourSpot> findUnvisited(@Param("userId") String userId,
                                 @Param("limit") int limit);
}
