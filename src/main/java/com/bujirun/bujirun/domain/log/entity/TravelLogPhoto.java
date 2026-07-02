package com.bujirun.bujirun.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "travel_log_photos")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TravelLogPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_log_item_id", nullable = false)
    private TravelLogItem travelLogItem;

    @Column(name = "photo_url", nullable = false)
    private String photoUrl;

    @Column(name = "is_representative", nullable = false)
    @Builder.Default
    private boolean representative = false;

    @Column(name = "order_index", nullable = false)
    @Builder.Default
    private int orderIndex = 0;

    public void setRepresentative(boolean representative) {
        this.representative = representative;
    }
}
