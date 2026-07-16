package com.bujirun.bujirun.domain.auth.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

import java.util.UUID;

// users 테이블과 매핑되는 엔티티
// 카카오 로그인뿐 아니라 자체 회원가입(local) 사용자도 이 테이블 하나로 같이 관리됨
@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA 내부용 빈 생성자. 외부에서 new User()로 무분별하게 생성하는 것을 막기 위해 접근 범위 제한
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID) // 기본키를 UUID로 자동 생성
    private UUID id;

    @Column(length = 50)
    private String nickname;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @Column(length = 255)
    private String email;

    // 로그인 방식 구분: "kakao" 또는 "local" 등
    @Column(name = "auth_provider", length = 20)
    private String authProvider;

    // 카카오 로그인 시, 카카오가 부여한 사용자 고유 ID (회원 식별 기준)
    @Column(name = "provider_id", length = 255)
    private String providerId;

    // 자체 회원가입(local) 시에만 사용. 카카오 로그인 사용자는 비워둠
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    // 탈퇴 시각. null이면 정상 유저, 값이 있으면 탈퇴한 유저
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    // 탈퇴 시 개인식별 정보 익명화 + soft delete
    public void anonymize() {
        this.nickname = null;
        this.profileImageUrl = null;
        this.email = null;
        this.providerId = null;  // 카카오 언링크 후에 호출해야 함
        this.deletedAt = LocalDateTime.now();
    }

    // 탈퇴 여부 확인. deleted_at이 null이 아니면 탈퇴한 유저
    public boolean isDeleted() {
        return this.deletedAt != null;
    }

    // 카카오 로그인 회원가입 시 사용하는 생성자
    // passwordHash는 의도적으로 제외 (카카오 로그인은 비밀번호가 없음)
    @Builder
    public User(String nickname, String profileImageUrl, String email, String authProvider, String providerId) {
        this.nickname = nickname;
        this.profileImageUrl = profileImageUrl;
        this.email = email;
        this.authProvider = authProvider;
        this.providerId = providerId;
    }

    public void updateNickname(String nickname) {
        this.nickname = nickname;
    }

    public void updateProfileImage(String profileImageUrl) {
        this.profileImageUrl = profileImageUrl;
    }
}