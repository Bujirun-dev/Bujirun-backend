package com.bujirun.bujirun.domain.log.repository;

import com.bujirun.bujirun.domain.log.entity.TravelLogPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TravelLogPhotoRepository extends JpaRepository<TravelLogPhoto, UUID> {

    @Modifying
    @Query("UPDATE TravelLogPhoto p SET p.representative = false WHERE p.travelLogItem.travelLog.id = :logId")
    void clearRepresentativeByLog(@Param("logId") UUID logId);
}
