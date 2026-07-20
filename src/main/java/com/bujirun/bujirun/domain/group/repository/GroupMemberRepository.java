package com.bujirun.bujirun.domain.group.repository;

import com.bujirun.bujirun.domain.group.entity.GroupMember;
import com.bujirun.bujirun.domain.group.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {

    boolean existsById_GroupIdAndId_UserId(UUID groupId, UUID userId);

    List<GroupMember> findById_GroupId(UUID groupId);

    List<GroupMember> findById_UserId(UUID userId);

    long countById_GroupId(UUID groupId);
}
