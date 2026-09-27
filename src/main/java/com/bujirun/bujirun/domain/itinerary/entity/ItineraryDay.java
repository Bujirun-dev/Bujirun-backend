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

    // 동시에 이 day를 고치는 서로 다른 편집(예: A는 3개로, B는 4개로 재구성)을 감지하기
    // 위한 낙관적 락. operationId 멱등키는 "같은 내용"의 중복 재전송만 걸러내고, 내용이
    // 다른 동시 요청은 나중 것이 앞의 것을 조용히 덮어쓰는 걸 막지 못했다(2026-09-16 이후
    // 감사에서 확인) — replaceDayItems/reorderItems가 이 값을 클라이언트가 마지막으로 읽은
    // 값(expectedVersion)과 비교해 어긋나면 409로 거부한다.
    @Version
    @Column(nullable = false)
    private Long version;

    @Builder.Default
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<ItineraryItem> items = new ArrayList<>();

    // 여행 기간이 수정되면 각 Day의 날짜도 새 시작일 기준으로 다시 맞춰야 한다.
    public void updateDate(LocalDate date) {
        this.date = date;
    }
}
