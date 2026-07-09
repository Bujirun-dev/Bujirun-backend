package com.bujirun.bujirun.domain.swipe.repository;

import com.bujirun.bujirun.domain.swipe.entity.SwipeSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SwipeSessionRepository extends JpaRepository<SwipeSession, UUID> {
}