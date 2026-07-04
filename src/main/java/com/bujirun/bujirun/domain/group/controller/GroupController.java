package com.bujirun.bujirun.domain.group.controller;

import com.bujirun.bujirun.domain.group.dto.request.CreateGroupRequest;
import com.bujirun.bujirun.domain.group.dto.request.JoinGroupRequest;
import com.bujirun.bujirun.domain.group.dto.response.GroupMemberResponse;
import com.bujirun.bujirun.domain.group.dto.response.GroupResponse;
import com.bujirun.bujirun.domain.group.service.GroupService;
import com.bujirun.bujirun.global.response.ApiResponse;
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

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;

    @PostMapping
    public ApiResponse<GroupResponse> create(
            @RequestBody @Valid CreateGroupRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.create(req, userId));
    }

    @PostMapping("/join")
    public ApiResponse<GroupResponse> join(
            @RequestBody @Valid JoinGroupRequest req,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.join(req, userId));
    }

    @GetMapping("/me")
    public ApiResponse<List<GroupResponse>> myGroups(@AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.getMyGroups(userId));
    }

    @GetMapping("/{groupId}/members")
    public ApiResponse<List<GroupMemberResponse>> members(
            @PathVariable UUID groupId,
            @AuthenticationPrincipal UUID userId) {
        return ApiResponse.ok(groupService.getMembers(groupId, userId));
    }
}
