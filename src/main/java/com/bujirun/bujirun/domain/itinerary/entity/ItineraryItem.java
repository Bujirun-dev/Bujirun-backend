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
    private String travelMode;         // walk / transit / taxi

    @Column(name = "travel_time_min")
    private Integer travelTimeMin;

    @Column(name = "route_type")
    private String routeType;          // "버스", "지하철", "도보", "택시" 등 원본값

    @Column(name = "route_no")
    private String routeNo;            // routeType=="버스"→버스 노선번호, routeType=="지하철"→지하철 노선명

    @Column(name = "start_station_name")
    private String startStationName;   // 출발 정류장/역명

    @Column(name = "end_station_name")
    private String endStationName;     // 도착 정류장/역명

    @Column(name = "start_ars_id")
    private String startArsId;         // 버스 정류장 ARS번호

    private String memo;

    // orderIndex는 Integer(nullable)로 받아 생략 시 그대로 둔다 — 예전엔 primitive int라
    // 요청 JSON에 orderIndex를 안 넣으면(예: 시간만 바꾸는 호출) Jackson이 0으로 채워 넣어
    // 순서를 조용히 0으로 되돌려버리는 문제가 있었다. 순서 자체는 이제 이 메서드가 아니라
    // reorderItems(전체 순서를 한 번에 원자 반영)로만 바꾸는 게 원칙 — [[itinerary-order-index-race]] 참고.
    public void update(Integer orderIndex, LocalTime arrivalTime, Integer durationMin,
                       String travelMode, Integer travelTimeMin, String memo) {
        if (orderIndex    != null) this.orderIndex    = orderIndex;
        if (arrivalTime   != null) this.arrivalTime   = arrivalTime;
        if (durationMin   != null) this.durationMin   = durationMin;
        if (travelMode    != null) this.travelMode    = travelMode;
        if (travelTimeMin != null) this.travelTimeMin = travelTimeMin;
        if (memo          != null) this.memo          = memo;
    }

    // day 전체 순서를 한 번의 트랜잭션으로 원자적으로 반영할 때만 사용(reorderItems 전용)
    public void updateOrder(int orderIndex) {
        this.orderIndex = orderIndex;
    }

    // 경로 상세(노선번호·정류장명 등)까지 함께 갱신할 때 사용
    public void updateRoute(String travelMode, Integer travelTimeMin,
                            String routeType, String routeNo,
                            String startStationName, String endStationName,
                            String startArsId) {
        if (travelMode        != null) this.travelMode        = travelMode;
        if (travelTimeMin     != null) this.travelTimeMin     = travelTimeMin;
        this.routeType         = routeType;          // 도보/택시면 null로 덮어써야 하니 무조건 대입
        this.routeNo           = routeNo;
        this.startStationName  = startStationName;
        this.endStationName    = endStationName;
        this.startArsId        = startArsId;
    }
}