package com.bujirun.bujirun.domain.visit.repository;

import com.bujirun.bujirun.domain.visit.entity.VisitPhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface VisitPhotoRepository extends JpaRepository<VisitPhoto, UUID> {

    List<VisitPhoto> findByVisitIdIn(Collection<UUID> visitIds);
}
