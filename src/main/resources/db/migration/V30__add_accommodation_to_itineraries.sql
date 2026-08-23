-- =============================================================
-- V30: itineraries에 숙소 정보 추가
-- 지금까지 프론트에서 localStorage로만 임시 보관하던 숙소명/주소를
-- 백엔드에 저장하도록 컬럼 추가.
-- =============================================================

ALTER TABLE itineraries
    ADD COLUMN IF NOT EXISTS accommodation_name    VARCHAR(255),
    ADD COLUMN IF NOT EXISTS accommodation_address  VARCHAR(255);
