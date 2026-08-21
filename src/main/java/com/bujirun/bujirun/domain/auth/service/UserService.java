package com.bujirun.bujirun.domain.auth.service;

import com.bujirun.bujirun.domain.auth.dto.request.UpdateProfileRequest;
import com.bujirun.bujirun.domain.auth.dto.response.NicknameAvailabilityResponse;
import com.bujirun.bujirun.domain.auth.dto.response.UserProfileResponse;
import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.exception.DuplicateNicknameException;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.log.service.TravelLogService;
import com.bujirun.bujirun.domain.visit.service.VisitService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    // 탈퇴 후 개인정보 완전 삭제까지의 유예기간 — 이 기간 안엔 카카오 재로그인으로 계정이
    // 복구될 수 있음(KakaoService, User.restore() 참고). 두 값은 같이 맞춰야 함
    private static final int PURGE_GRACE_PERIOD_DAYS = 30;

    private final UserRepository userRepository;
    private final KakaoUnlinkService kakaoUnlinkService;
    private final TravelLogService travelLogService;
    private final VisitService visitService;

    public UserProfileResponse getProfile(UUID userId) {
        return UserProfileResponse.from(findUser(userId));
    }

    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest req) {
        User user = findUser(userId);

        if (req.nickname() != null && !req.nickname().equals(user.getNickname())) {
            if (req.nickname().isBlank()) {
                throw new IllegalArgumentException("닉네임은 공백일 수 없습니다.");
            }
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
     * 닉네임은 유지, 여행 일정(Itinerary)은 보존됨.
     * 방문 인증 기록(Visit·GPS)과 여행 기록(TravelLog)은 30일 유예기간이 지나야
     * purgeExpiredAccountData()에서 완전 삭제됨 — 그 전엔 재로그인으로 계정 복구 가능
     */
    @Transactional
    public void deleteAccount(UUID userId) {
        User user = findUser(userId);

        // 카카오 연결 해제 (providerId 필요하므로 먼저 실행)
        if (user.getProviderId() != null) {
            kakaoUnlinkService.unlink(user.getProviderId());
        }

        // 본인의 travel_logs 비공개 처리 — 유예기간 동안 다른 유저에게 노출되지 않도록
        travelLogService.setUserLogsPrivate(userId);

        // 개인정보 익명화 + soft delete
        user.anonymize();
    }

    /**
     * 탈퇴 후 {@value #PURGE_GRACE_PERIOD_DAYS}일이 지난 계정의 개인정보를 완전 삭제한다.
     * 대상: 방문 인증 기록(Visit, GPS 좌표 포함)과 인증 사진(VisitPhoto), 여행 기록(TravelLog)
     * 및 그 하위 사진·해시태그. User row 자체와 여행 일정(Itinerary)은 삭제하지 않음
     * — travel_logs/visits는 itinerary_id를 참조만 할 뿐 itinerary를 소유하지 않아
     * (반대 방향 FK·cascade 없음) 이 정리 작업이 일정 탭에 영향을 주지 않는다.
     * 스케줄러(AccountPurgeScheduler)가 주기적으로 호출.
     */
    @Transactional
    public int purgeExpiredAccountData() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(PURGE_GRACE_PERIOD_DAYS);
        List<User> expired = userRepository.findByDeletedAtIsNotNullAndDeletedAtBefore(threshold);

        for (User user : expired) {
            visitService.deleteAllByUser(user.getId());
            travelLogService.deleteAllByUser(user.getId());
        }

        if (!expired.isEmpty()) {
            log.info("탈퇴 {}일 경과 계정 개인정보 삭제 완료: {}건", PURGE_GRACE_PERIOD_DAYS, expired.size());
        }
        return expired.size();
    }

    // 탈퇴한 유저는 조회 불가
    private User findUser(UUID userId) {
        return userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new EntityNotFoundException("사용자를 찾을 수 없습니다. id=" + userId));
    }
}