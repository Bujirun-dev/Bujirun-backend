package com.bujirun.bujirun.domain.auth.repository;

import com.bujirun.bujirun.domain.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, java.util.UUID> {

    // 카카오 로그인 시 기존 회원인지 조회용 (탈퇴 유저 포함)
    Optional<User> findByProviderIdAndAuthProvider(String providerId, String authProvider);

    // 탈퇴하지 않은 유저만 조회 (soft delete 적용)
    Optional<User> findByIdAndDeletedAtIsNull(java.util.UUID id);

    // 카카오 재로그인 시 탈퇴하지 않은 유저만 조회 (soft delete 적용)
    // 탈퇴한 유저(deleted_at IS NOT NULL)는 제외하여 30일 유예기간 동안 재가입 방지
    Optional<User> findByProviderIdAndAuthProviderAndDeletedAtIsNull(String providerId, String authProvider);

    // 닉네임 중복 검사용 (탈퇴하지 않은 유저 기준)
    boolean existsByNicknameAndDeletedAtIsNull(String nickname);

    // 탈퇴 후 유예기간(30일)이 지난 계정 조회 — 개인정보(방문 인증/GPS, 여행 기록) 완전 삭제 배치용
    List<User> findByDeletedAtIsNotNullAndDeletedAtBefore(LocalDateTime threshold);
}