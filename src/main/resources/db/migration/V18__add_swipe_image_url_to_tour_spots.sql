-- V18__add_swipe_image_url_to_tour_spots.sql
-- 스와이프 덱(/api/collections/swipe-deck)용으로 별도로 큐레이션한 이미지를 저장하는 컬럼.
-- 기존 thumbnail_url(관광공사 API 동기화 값)은 그대로 두고, 스와이프 화면에서만 이 이미지를 우선 사용한다.
-- 실제 관광지별 이미지 값은 계속 늘어나는 데이터이므로 마이그레이션에 하드코딩하지 않고,
-- 이미지 준비될 때마다 프로덕션 DB에 직접 UPDATE로 채운다. 여기서는 스키마만 추가.

ALTER TABLE tour_spots ADD COLUMN IF NOT EXISTS swipe_image_url VARCHAR(500);
