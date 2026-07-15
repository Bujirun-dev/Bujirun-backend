package com.bujirun.bujirun.domain.swipe.entity;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "swipe_results")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class SwipeResult {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private SwipeSession session;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private TourSpot spot;

    @Column(nullable = false)
    private boolean liked;

    @Column(name = "swiped_at", nullable = false)
    private LocalDateTime swipedAt;

    @PrePersist
    public void prePersist() {
        this.swipedAt = LocalDateTime.now();
    }
}