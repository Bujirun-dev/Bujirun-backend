package com.bujirun.bujirun.domain.auth.service;

import com.bujirun.bujirun.domain.auth.dto.request.UpdateProfileRequest;
import com.bujirun.bujirun.domain.auth.dto.response.UserProfileResponse;
import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final KakaoUnlinkService kakaoUnlinkService;

    public UserProfileResponse getProfile(UUID userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = findUser(userId);

        if (req.nickname() != null) {
            user.updateNickname(req.nickname());
        }
        if (req.profileImageUrl() != null) {
            user.updateProfileImage(req.profileImageUrl());
        }

        return UserProfileResponse.from(user);
    }

    /**
     * 회원탈퇴: 카카오 연결 해제 후 개인정보 익명화 (soft delete)
     * 여행 일정, 방문 기록 등 공유 데이터는 유지됨
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findUser(userId);

        // 카카오 연결 해제 먼저 (anonymize() 호출 전에 providerId 필요)
        if (user.getProviderId() != null) {
            kakaoUnlinkService.unlink(user.getProviderId());
        }

        // 개인식별 정보 익명화 + deleted_at 세팅
        user.anonymize();
    }
    //탈퇴한 유저는 조회 불가
    private User findUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));
    }
}
