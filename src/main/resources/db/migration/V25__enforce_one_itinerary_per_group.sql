-- V25: 그룹당 일정은 하나만 존재하도록 강제 (여행 하나당 그룹 하나 정책).
-- 개인 일정(group_id IS NULL)은 여러 개 있을 수 있으므로 부분 유니크 인덱스로 group_id가 있는 행만 제한.
CREATE UNIQUE INDEX itineraries_group_id_unique ON itineraries(group_id) WHERE group_id IS NOT NULL;
