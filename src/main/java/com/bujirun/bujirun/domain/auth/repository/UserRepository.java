package com.bujirun.bujirun.domain.auth.repository;

import com.bujirun.bujirun.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, java.util.UUID> {

    // 카카오 로그인 시 기존 회원인지 조회용
    Optional<User> findByProviderIdAndAuthProvider(String providerId, String authProvider);
}