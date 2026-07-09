package com.bujirun.bujirun.domain.bookmark.entity;

import com.bujirun.bujirun.domain.auth.entity.User;
import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookmarks")
@IdClass(BookmarkId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Bookmark {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private TourSpot spot;

    @Column(name = "bookmarked_at", nullable = false)
    private LocalDateTime bookmarkedAt;

    @Builder
    private Bookmark(User user, TourSpot spot) {
        this.user = user;
        this.spot = spot;
        this.bookmarkedAt = LocalDateTime.now();
    }
}
