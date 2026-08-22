package com.bujirun.bujirun.domain.group.service;

import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.auth.repository.UserRepository;
import com.bujirun.bujirun.domain.group.dto.request.CreateGroupRequest;
import com.bujirun.bujirun.domain.group.dto.request.JoinGroupRequest;
import com.bujirun.bujirun.domain.group.dto.response.GroupInvitePreviewResponse;
import com.bujirun.bujirun.domain.group.dto.response.GroupMemberResponse;
import com.bujirun.bujirun.domain.group.dto.response.GroupResponse;
import com.bujirun.bujirun.domain.group.entity.Group;
import com.bujirun.bujirun.domain.group.entity.GroupMember;
import com.bujirun.bujirun.domain.group.repository.GroupMemberRepository;
import com.bujirun.bujirun.domain.group.repository.GroupRepository;
import com.bujirun.bujirun.domain.itinerary.repository.ItineraryRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    private static final String INVITE_CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // 혼동되는 0/O/1/I 제외
    private static final int INVITE_CODE_LENGTH = 8;
    private static final int INVITE_CODE_MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final ItineraryRepository itineraryRepository;

    @Transactional
    public GroupResponse create(CreateGroupRequest req, UUID userId) {
        Group group = groupRepository.save(Group.builder()
                .name(req.name())
                .inviteCode(generateUniqueInviteCode())
                .createdBy(userId)
                .build());

        groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(userId)
                .build());

        return GroupResponse.from(group);
    }

    @Transactional
    public GroupResponse join(JoinGroupRequest req, UUID userId) {
        Group group = groupRepository.findByInviteCode(req.inviteCode())
                .orElseThrow(() -> new EntityNotFoundException("초대 코드를 찾을 수 없습니다."));

        if (!groupMemberRepository.existsById_GroupIdAndId_UserId(group.getId(), userId)) {
            groupMemberRepository.save(GroupMember.builder()
                    .groupId(group.getId())
                    .userId(userId)
                    .build());
        }

        return GroupResponse.from(group);
    }

    public GroupInvitePreviewResponse previewByInviteCode(String inviteCode) {
        Group group = groupRepository.findByInviteCode(inviteCode)
                .orElseThrow(() -> new EntityNotFoundException("초대 코드를 찾을 수 없습니다."));

        String inviterNickname = userRepository.findById(group.getCreatedBy())
                .map(User::getNickname)
                .orElse(null);

        long memberCount = groupMemberRepository.countById_GroupId(group.getId());

        return new GroupInvitePreviewResponse(group.getName(), inviterNickname, memberCount);
    }

    public List<GroupResponse> getMyGroups(UUID userId) {
        return groupMemberRepository.findById_UserId(userId).stream()
                .map(gm -> groupRepository.findById(gm.getId().getGroupId()).orElseThrow())
                .map(GroupResponse::from)
                .toList();
    }

    public List<GroupMemberResponse> getMembers(UUID groupId, UUID userId) {
        validateMember(groupId, userId);

        UUID leaderId = groupRepository.findById(groupId)
                .map(Group::getCreatedBy)
                .orElse(null);

        return groupMemberRepository.findById_GroupId(groupId).stream()
                .map(gm -> {
                    UUID memberId = gm.getId().getUserId();
                    User member = userRepository.findById(memberId).orElse(null);
                    String nickname = member != null ? member.getNickname() : null;
                    String profileImageUrl = member != null ? member.getProfileImageUrl() : null;
                    return new GroupMemberResponse(
                            memberId, nickname, profileImageUrl, gm.getJoinedAt(), memberId.equals(leaderId));
                })
                .toList();
    }

    public void validateMember(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsById_GroupIdAndId_UserId(groupId, userId)) {
            throw new IllegalArgumentException("그룹 멤버만 접근할 수 있습니다.");
        }
    }

    // 그룹에서 나가기. 남은 멤버가 없어지면(마지막 1인이 나가는 경우) 그룹의 일정과 그룹 자체를 함께 삭제한다.
    @Transactional
    public void leave(UUID groupId, UUID userId) {
        validateMember(groupId, userId);

        long memberCount = groupMemberRepository.countById_GroupId(groupId);
        if (memberCount <= 1) {
            itineraryRepository.deleteAll(itineraryRepository.findByGroupId(groupId));
            groupRepository.deleteById(groupId); // group_members는 ON DELETE CASCADE로 함께 삭제됨
        } else {
            groupMemberRepository.deleteById_GroupIdAndId_UserId(groupId, userId);
        }
    }

    private String generateUniqueInviteCode() {
        for (int attempt = 0; attempt < INVITE_CODE_MAX_ATTEMPTS; attempt++) {
            String code = generateInviteCode();
            if (groupRepository.findByInviteCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("초대 코드 생성에 실패했습니다. 다시 시도해주세요.");
    }

    private String generateInviteCode() {
        StringBuilder sb = new StringBuilder(INVITE_CODE_LENGTH);
        for (int i = 0; i < INVITE_CODE_LENGTH; i++) {
            sb.append(INVITE_CODE_CHARS.charAt(RANDOM.nextInt(INVITE_CODE_CHARS.length())));
        }
        return sb.toString();
    }
}
