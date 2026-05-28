package com.bujirun.bujirun.domain.spot.repository;

import com.bujirun.bujirun.domain.spot.entity.Sigungu;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SigunguRepository extends JpaRepository<Sigungu, Integer> {
    Optional<Sigungu> findByCode(String code);
}