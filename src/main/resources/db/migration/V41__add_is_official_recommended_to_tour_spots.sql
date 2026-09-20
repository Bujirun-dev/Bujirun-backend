-- V41__add_is_official_recommended_to_tour_spots.sql
-- 부산명소정보 API(data.go.kr 15063481)에 등재된 공식 관광지인지 여부를 표시하는 컬럼.
-- 도감(60개) 대상으로 좌표·명칭 매칭 배치(MigrationService#matchOfficialRecommendedSpots)를 돌려
-- content_id 기준으로 값을 채운다 (내부 UUID 아님). 실제 매칭은 배치 실행 시점에 처리하므로
-- 이 마이그레이션은 컬럼만 만든다.

ALTER TABLE tour_spots ADD COLUMN IF NOT EXISTS is_official_recommended BOOLEAN NOT NULL DEFAULT false;
