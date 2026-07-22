-- V21: 방문 인증(visits)이 어느 일정의 어느 항목 방문이었는지 연결할 수 있도록 컬럼 추가.
-- 같은 관광지를 여러 일정에서 각각 인증한 경우, 그중 특정 일정에서 찍은 인증사진만
-- 골라 쓸 수 있게 하기 위함(예: 여행 종료 후 영수증에 그 일정에서 찍은 사진만 반영).
-- 특정 일정과 무관하게 인증만 하는 기존 흐름도 계속 지원해야 하므로 nullable.
-- 일정 항목이 나중에 삭제되어도 방문 인증 기록 자체는 남아야 하므로 ON DELETE SET NULL.

ALTER TABLE visits
    ADD COLUMN itinerary_item_id UUID REFERENCES itinerary_items(id) ON DELETE SET NULL;

CREATE INDEX idx_visits_itinerary_item ON visits(itinerary_item_id);
