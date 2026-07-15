-- =============================================================
-- V14: 그룹 일정에서는 그룹원 각자가 자신만의 여행 기록을 남길 수 있어야 하므로,
-- travel_logs의 "일정당 1개" 제약을 "일정+작성자당 1개"로 완화한다.
-- =============================================================

ALTER TABLE travel_logs
    DROP CONSTRAINT uq_travel_logs_itinerary_id;

ALTER TABLE travel_logs
    ADD CONSTRAINT uq_travel_logs_itinerary_id_user_id UNIQUE (itinerary_id, user_id);
