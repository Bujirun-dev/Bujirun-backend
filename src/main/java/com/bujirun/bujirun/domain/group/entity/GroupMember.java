package com.bujirun.bujirun.domain.group.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "group_members")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GroupMember {

    @EmbeddedId
    private GroupMemberId id;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    @Builder
    public GroupMember(UUID groupId, UUID userId) {
        this.id = new GroupMemberId(groupId, userId);
    }

    @PrePersist
    void prePersist() {
        this.joinedAt = LocalDateTime.now();
    }
}
