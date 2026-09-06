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
    @OrderBy("orderIndex ASC")
    @Fetch(FetchMode.SUBSELECT)
    private List<ItineraryItem> items = new ArrayList<>();

    // 여행 기간이 수정되면 각 Day의 날짜도 새 시작일 기준으로 다시 맞춰야 한다.
    public void updateDate(LocalDate date) {
        this.date = date;
    }
}
