package com.bujirun.bujirun.domain.log.repository;

import com.bujirun.bujirun.domain.log.entity.TravelLogHashtag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TravelLogHashtagRepository extends JpaRepository<TravelLogHashtag, UUID> {
}
