-- V28: 그룹 일정 동시편집 중 발생한 order_index 충돌(같은 day_id+order_index가 서로 다른
-- 항목에 중복 저장된 상태) 복구. 원인은 프론트가 항목별 PATCH로 순서를 나눠 반영하던
-- 방식이라 여러 클라이언트가 거의 동시에 flush하면 부분 반영이 뒤섞일 수 있었던 것
-- (앱 코드는 항목 순서를 day 단위로 한 번에 원자 반영하는 방식으로 별도 수정됨).
--
-- day 단위로 order_index ASC, arrival_time ASC(NULL은 맨 뒤), id ASC 순으로 1..N 재정렬한다.
-- 이미 정상인 day는 값이 그대로라 실질적인 변경이 없다.
WITH renumbered AS (
    SELECT id,
           ROW_NUMBER() OVER (
               PARTITION BY day_id
               ORDER BY order_index ASC, arrival_time ASC NULLS LAST, id ASC
           ) AS new_order
    FROM itinerary_items
)
UPDATE itinerary_items ii
SET order_index = renumbered.new_order
FROM renumbered
WHERE ii.id = renumbered.id
  AND ii.order_index <> renumbered.new_order;
