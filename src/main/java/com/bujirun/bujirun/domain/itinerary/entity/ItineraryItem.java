package com.bujirun.bujirun.domain.itinerary.entity;

import com.bujirun.bujirun.domain.spot.entity.TourSpot;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "itinerary_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ItineraryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "day_id", nullable = false)
    private ItineraryDay day;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id", nullable = false)
    private TourSpot spot;

    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @Column(name = "arrival_time")
    private LocalTime arrivalTime;

    @Column(name = "duration_min")
    private Integer durationMin;

    @Column(name = "travel_mode")
    private String travelMode;

    @Column(name = "travel_time_min")
    private Integer travelTimeMin;

    private String memo;

    public void update(int orderIndex, LocalTime arrivalTime, Integer durationMin,
                       String travelMode, Integer travelTimeMin, String memo) {
        this.orderIndex    = orderIndex;
        this.arrivalTime   = arrivalTime;
        this.durationMin   = durationMin;
        this.travelMode    = travelMode;
        this.travelTimeMin = travelTimeMin;
        this.memo          = memo;
    }
}
