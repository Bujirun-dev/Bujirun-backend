-- 직전 장소 → 이 장소 구간의 이동 요금(원). NULL은 요금을 모르는 경우(컬럼 추가 전 저장된 항목 등)
ALTER TABLE itinerary_items ADD COLUMN travel_fare INTEGER;
