-- =============================================================
-- V17: 영수증에 "몇 번째 여행"인지 표시하기 위해 travel_number 추가.
-- 삭제 등으로 순서가 바뀌어도 영수증에 찍힌 번호가 변하지 않도록,
-- 생성 시점에 값을 확정해서 저장한다(조회 시 계산하지 않음).
-- =============================================================

ALTER TABLE travel_logs ADD COLUMN travel_number INTEGER;

WITH numbered AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY user_id ORDER BY created_at, id) AS rn
    FROM travel_logs
)
UPDATE travel_logs t
SET travel_number = numbered.rn
FROM numbered
WHERE t.id = numbered.id;

ALTER TABLE travel_logs ALTER COLUMN travel_number SET NOT NULL;
