-- V34: 그룹 일정 확정(ItineraryVoteService.saveConfirmedItinerary)이 ItineraryDay 생성 시
-- date를 채우지 않던 버그(2026-08-27 발견, 애플리케이션 코드는 같은 커밋에서 수정됨)로 인해
-- 이미 저장된 itinerary_days 중 date가 NULL인 행들을 itineraries.start_at + (day_number-1)로 복구.
-- (영수증 등에서 day별 날짜가 안 보이던 원인)

UPDATE itinerary_days d
SET date = i.start_at + (d.day_number - 1)
FROM itineraries i
WHERE d.itinerary_id = i.id
  AND d.date IS NULL
  AND i.start_at IS NOT NULL;
