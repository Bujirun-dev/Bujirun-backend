package com.bujirun.bujirun.domain.itinerary.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(
    name = "itinerary_days",
    uniqueConstraints = @UniqueConstraint(columnNames = {"itinerary_id", "day_number"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "itinerary_id", nullable = false)
    private Itinerary itinerary;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    private LocalDate date;

    @Builder.Default
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    // 방문 항목의 순서 기준은 order_index 하나뿐이다(시각으로 정렬하지 않는다 — 같은 날 같은
    // 시각이 저장되는 것도 허용하므로). 단 과거에 동시편집으로 order_index가 중복 저장된
    // 데이터가 실제로 있어(2026-08-12 프로덕션) 그 경우 순서가 조회마다 흔들릴 수 있으므로,
    // 방문 시각을 tiebreaker로 둬서 최소한 결정적인 순서가 나오게 한다.
    @OrderBy("orderIndex ASC, arrivalTime ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<ItineraryItem> items = new ArrayList<>();

    // 여행 기간이 수정되면 각 Day의 날짜도 새 시작일 기준으로 다시 맞춰야 한다.
    public void updateDate(LocalDate date) {
        this.date = date;
    }
}
