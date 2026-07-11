-- =============================================================
-- V12: travel_logs는 일정(itinerary)당 1개만 존재해야 함 — 동시 요청 등으로
-- 애플리케이션 체크를 우회해서 중복 생성되는 것을 DB 레벨에서 막기 위한 제약
-- =============================================================

ALTER TABLE travel_logs
    ADD CONSTRAINT uq_travel_logs_itinerary_id UNIQUE (itinerary_id);
