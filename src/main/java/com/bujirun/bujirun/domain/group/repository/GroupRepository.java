package com.bujirun.bujirun.domain.group.repository;

import com.bujirun.bujirun.domain.group.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface GroupRepository extends JpaRepository<Group, UUID> {

    Optional<Group> findByInviteCode(String inviteCode);
}
