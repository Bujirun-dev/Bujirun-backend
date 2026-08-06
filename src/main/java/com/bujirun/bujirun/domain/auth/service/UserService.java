package com.bujirun.bujirun.domain.auth.service;

import com.bujirun.bujirun.domain.auth.dto.request.UpdateProfileRequest;
import com.bujirun.bujirun.domain.auth.dto.response.NicknameAvailabilityResponse;
import com.bujirun.bujirun.domain.auth.dto.response.UserProfileResponse;
import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.exception.DuplicateNicknameException;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.log.service.TravelLogService;
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
    private final TravelLogService travelLogService;

    public UserProfileResponse getProfile(UUID userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = findUser(userId);

        if (req.nickname() != null && !req.nickname().equals(user.getNickname())) {
            if (userRepository.existsByNicknameAndDeletedAtIsNull(req.nickname())) {
                throw new DuplicateNicknameException("이미 사용 중인 닉네임입니다.");
            }
            user.updateNickname(req.nickname());
        }
        if (req.profileImageUrl() != null) {
            user.updateProfileImage(req.profileImageUrl());
        }

        return UserProfileResponse.from(user);
    }

    // 닉네임 변경 전 미리 중복 여부만 확인(실제 변경은 안 함). 지금 쓰는 닉네임 그대로 조회해도
    // updateProfile과 동일하게 사용 가능(available=true) 처리.
    public NicknameAvailabilityResponse checkNicknameAvailability(UUID userId, String nickname) {
        User user = findUser(userId);

        boolean available = nickname.equals(user.getNickname())
                || !userRepository.existsByNicknameAndDeletedAtIsNull(nickname);

        return new NicknameAvailabilityResponse(available);
    }

    /**
     * 회원탈퇴: 카카오 연결 해제 → 로그 비공개 처리 → 개인정보 익명화
     * 닉네임은 유지, 여행 일정은 보존됨
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findUser(userId);

        // 카카오 연결 해제 (providerId 필요하므로 먼저 실행)
        if (user.getProviderId() != null) {
            kakaoUnlinkService.unlink(user.getProviderId());
        }

        // 본인의 travel_logs 비공개 처리
        travelLogService.setUserLogsPrivate(userId);

        // 개인정보 익명화 + soft delete
        user.anonymize();
    }

    // 탈퇴한 유저는 조회 불가
    private User findUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));
    }
}