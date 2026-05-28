package com.bujirun.bujirun.domain.spot.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sigungu")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class Sigungu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, unique = true)
    private String code;    // TourAPI sigunguCode (예: "9")

    @Column(nullable = false)
    private String name;    // 구군명 (예: "해운대구")
}
