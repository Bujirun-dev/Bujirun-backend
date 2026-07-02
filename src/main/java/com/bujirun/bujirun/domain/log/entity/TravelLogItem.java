package com.bujirun.bujirun.domain.log.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "travel_log_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TravelLogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_log_id", nullable = false)
    private TravelLog travelLog;

    @Column(name = "itinerary_item_id", nullable = false)
    private UUID itineraryItemId;

    @Builder.Default
    @OneToMany(mappedBy = "travelLogItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<TravelLogPhoto> photos = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "travelLogItem", cascade = CascadeType.ALL, orphanRemoval = true)
    @Fetch(FetchMode.SUBSELECT)
    private List<TravelLogHashtag> hashtags = new ArrayList<>();
}
