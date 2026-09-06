-- =============================================================
-- V31: itineraries에 숙소 좌표 추가
-- 숙소 기준 동선 최적화 기능을 위해 숙소의 위도/경도를 저장.
-- =============================================================

ALTER TABLE itineraries
    ADD COLUMN IF NOT EXISTS accommodation_lat DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS accommodation_lng DOUBLE PRECISION;
