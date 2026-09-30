package com.bujirun.bujirun.domain.itinerary.generate.service;

import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class ItineraryGenerateServiceTest {

    @Test
    void 좋아요가_30곳_미만이면_나머지_자리를_후보로_채운다() {
        assertThat(ItineraryGenerateService.remainingCandidateSlots(0)).isEqualTo(30);
        assertThat(ItineraryGenerateService.remainingCandidateSlots(12)).isEqualTo(18);
    }

    @Test
    void 좋아요가_30곳_이상이면_추가_후보는_0개다() {
        assertThat(ItineraryGenerateService.remainingCandidateSlots(30)).isZero();
        assertThat(ItineraryGenerateService.remainingCandidateSlots(31)).isZero();
        assertThat(ItineraryGenerateService.remainingCandidateSlots(80)).isZero();
    }

    @Test
    void 좋아요가_30곳을_넘어도_후보_자르기에서_예외가_나지_않는다() {
        // 2026-09-30 운영: 그룹 좋아요 합계 31곳 → Stream.limit(-1) → IllegalArgumentException("-1") → 400
        assertThatCode(() -> Stream.of("a", "b").limit(ItineraryGenerateService.remainingCandidateSlots(31)).toList())
                .doesNotThrowAnyException();
    }
}
