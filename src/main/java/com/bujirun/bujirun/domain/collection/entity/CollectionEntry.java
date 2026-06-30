package com.bujirun.bujirun.domain.collection.entity;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import com.bujirun.bujirun.domain.auth.entity.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "collection_entries")
@IdClass(CollectionEntryId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectionEntry {

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private TourSpot spot;

    @Column(nullable = false)
    private boolean collected;

    private LocalDateTime collectedAt;

    @Builder
    private CollectionEntry(User user, TourSpot spot) {
        this.user = user;
        this.spot = spot;
        this.collected = false;
    }

    public void markCollected() {
        this.collected = true;
        this.collectedAt = LocalDateTime.now();
    }

    public void cancelCollection() {
        this.collected = false;
        this.collectedAt = null;
    }
}