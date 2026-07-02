package com.bujirun.bujirun.domain.log.repository;

import com.bujirun.bujirun.domain.log.entity.TravelLogItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TravelLogItemRepository extends JpaRepository<TravelLogItem, UUID> {
    List<TravelLogItem> findByTravelLogId(UUID travelLogId);
    Optional<TravelLogItem> findByTravelLogIdAndItineraryItemId(UUID travelLogId, UUID itineraryItemId);

    @Query("SELECT DISTINCT i.travelLog.id FROM TravelLogItem i WHERE i.itineraryItemId IN :itemIds")
    List<UUID> findDistinctTravelLogIdsByItineraryItemIdIn(@Param("itemIds") Collection<UUID> itemIds);
}
