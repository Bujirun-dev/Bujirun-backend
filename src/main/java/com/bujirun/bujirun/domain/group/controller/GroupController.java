package com.bujirun.bujirun.domain.group.controller;

import com.bujirun.bujirun.domain.group.dto.request.CreateGroupRequest;
import com.bujirun.bujirun.domain.group.dto.request.JoinGroupRequest;
import com.bujirun.bujirun.domain.group.dto.response.GroupMemberResponse;
import com.bujirun.bujirun.domain.group.dto.response.GroupResponse;
import com.bujirun.bujirun.domain.group.service.GroupService;
import com.bujirun.bujirun.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "그룹", description = "여행 그룹 생성 및 참여, 멤버 조회 API")
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @Operation(summary = "그룹 생성", description = "새로운 여행 그룹을 생성합니다.")
    @PostMapping
    public ApiResponse<GroupResponse> create(
            @RequestBody @Valid CreateGroupRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.create(req, userId));
    }

    @Operation(summary = "그룹 참여", description = "초대 코드를 이용해 기존 그룹에 참여합니다.")
    @PostMapping("/join")
    public ApiResponse<GroupResponse> join(
            @RequestBody @Valid JoinGroupRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.join(req, userId));
    }

    @Operation(summary = "내 그룹 목록 조회", description = "현재 로그인한 사용자가 속한 그룹 목록을 조회합니다.")
    @GetMapping("/me")
    public ApiResponse<List<GroupResponse>> myGroups(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.getMyGroups(userId));
    }

    @Operation(summary = "그룹 멤버 조회", description = "특정 그룹에 속한 멤버 목록을 조회합니다.")
    @GetMapping("/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> members(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.getMembers(groupId, userId));
    }
}
