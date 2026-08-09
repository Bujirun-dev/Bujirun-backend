-- V27: itinerary_items(day_id, spot_id) 중복 저장 버그 수정.

-- 1) 기존 중복 row 정리: (day_id, spot_id) 그룹에서 order_index가 가장 작은
--    row만 남기고 나머지는 삭제 (동률이면 id로 타이브레이크).
WITH ranked AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY day_id, spot_id
               ORDER BY order_index ASC, id ASC
           ) AS rn
    FROM itinerary_items
)
DELETE FROM itinerary_items
WHERE id IN (SELECT id FROM ranked WHERE rn > 1);

-- 2) 중복 삭제로 생긴 order_index 공백을 day 단위로 1..N 재정렬.
WITH renumbered AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY day_id ORDER BY order_index ASC, id ASC) AS new_order
    FROM itinerary_items
)
UPDATE itinerary_items ii
SET order_index = renumbered.new_order
FROM renumbered
WHERE ii.id = renumbered.id
  AND ii.order_index <> renumbered.new_order;

-- 3) 재발 방지: DB 레벨 최종 방어선.
ALTER TABLE itinerary_items
    ADD CONSTRAINT uq_itinerary_items_day_spot UNIQUE (day_id, spot_id);
