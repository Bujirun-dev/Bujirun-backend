-- =============================================================
-- V11: 여행 리뷰(영수증 발행) 화면을 위한 mood/theme 컬럼 추가
-- =============================================================

ALTER TABLE travel_logs
    ADD COLUMN IF NOT EXISTS mood  INT,
    ADD COLUMN IF NOT EXISTS theme VARCHAR(50);
