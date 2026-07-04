package com.bujirun.bujirun.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "travel_log_hashtags")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TravelLogHashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_log_item_id", nullable = false)
    private TravelLogItem travelLogItem;

    @Column(nullable = false, length = 50)
    private String tag;
}
