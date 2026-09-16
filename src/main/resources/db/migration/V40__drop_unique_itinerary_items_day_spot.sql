-- V40: 같은 day에 동일한 관광지를 여러 번 추가할 수 있도록 허용(사용자 요청).
-- V27에서 동시편집 중복저장 버그 재발 방지용으로 추가했던 제약조건을 제거한다.
-- 애플리케이션 레벨 중복 체크(ItineraryService.addItem)도 같은 이유로 제거됨.

ALTER TABLE itinerary_items
    DROP CONSTRAINT uq_itinerary_items_day_spot;
