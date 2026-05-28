package com.bujirun.bujirun.domain.spot.repository;

import com.bujirun.bujirun.domain.spot.entity.TourSpotTag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TourSpotTagRepository extends JpaRepository<TourSpotTag, UUID> {

    List<TourSpotTag> findBySpotId(UUID spotId);

    void deleteBySpotId(UUID spotId);
}