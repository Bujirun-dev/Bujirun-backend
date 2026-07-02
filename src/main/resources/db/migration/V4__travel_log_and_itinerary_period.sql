-- =============================================================
-- V4: 여행 로그 도메인 재설계 + 일정 기간 필드 추가
-- =============================================================


-- [1] tour_spots: 도감 수집 대상 여부 컬럼 추가 (feat/collection-crud 반영)
ALTER TABLE tour_spots
    ADD COLUMN IF NOT EXISTS is_collection BOOLEAN NOT NULL DEFAULT false;


-- [2] itineraries: 여행 시작일/종료일 추가
ALTER TABLE itineraries
    ADD COLUMN IF NOT EXISTS start_at DATE,
    ADD COLUMN IF NOT EXISTS end_at   DATE;


-- [3] 기존 travel_logs 관련 테이블 제거 (V1 초기 설계 → 재설계)
DROP TABLE IF EXISTS log_imports    CASCADE;
DROP TABLE IF EXISTS log_likes      CASCADE;
DROP TABLE IF EXISTS travel_logs    CASCADE;


-- [4] travel_logs 재생성
CREATE TABLE travel_logs (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    itinerary_id        UUID        NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
    user_id             UUID        NOT NULL REFERENCES users(id),
    is_public           BOOLEAN     NOT NULL DEFAULT false,
    thumbnail_photo_url TEXT,
    added_count         INT         NOT NULL DEFAULT 0,
    created_at          TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP   NOT NULL DEFAULT now()
);


-- [5] travel_log_items: 이티너리 아이템별 로그 항목
CREATE TABLE travel_log_items (
    id                  UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
    travel_log_id       UUID    NOT NULL REFERENCES travel_logs(id) ON DELETE CASCADE,
    itinerary_item_id   UUID    NOT NULL
);


-- [6] travel_log_photos: 항목별 사진
CREATE TABLE travel_log_photos (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    travel_log_item_id  UUID        NOT NULL REFERENCES travel_log_items(id) ON DELETE CASCADE,
    photo_url           TEXT        NOT NULL,
    is_representative   BOOLEAN     NOT NULL DEFAULT false,
    order_index         INT         NOT NULL DEFAULT 0
);


-- [7] travel_log_hashtags: 항목별 해시태그
CREATE TABLE travel_log_hashtags (
    id                  UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    travel_log_item_id  UUID        NOT NULL REFERENCES travel_log_items(id) ON DELETE CASCADE,
    tag                 VARCHAR(50) NOT NULL
);


-- [8] 인덱스
CREATE INDEX idx_travel_logs_user       ON travel_logs(user_id);
CREATE INDEX idx_travel_logs_public     ON travel_logs(is_public, created_at DESC);
CREATE INDEX idx_travel_logs_popular    ON travel_logs(is_public, added_count DESC);
CREATE INDEX idx_travel_log_items_log   ON travel_log_items(travel_log_id);
CREATE INDEX idx_travel_log_items_item  ON travel_log_items(itinerary_item_id);
CREATE INDEX idx_travel_log_photos_item ON travel_log_photos(travel_log_item_id);
CREATE INDEX idx_travel_log_tags_item   ON travel_log_hashtags(travel_log_item_id);
