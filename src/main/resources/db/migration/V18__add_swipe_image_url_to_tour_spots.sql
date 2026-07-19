-- V18__add_swipe_image_url_to_tour_spots.sql
-- 스와이프 덱(/api/collections/swipe-deck)용으로 별도로 큐레이션한 이미지를 저장하는 컬럼.
-- 기존 thumbnail_url(관광공사 API 동기화 값)은 그대로 두고, 스와이프 화면에서만 이 이미지를 우선 사용한다.
-- content_id는 varchar이므로 문자열로 비교.

ALTER TABLE tour_spots ADD COLUMN IF NOT EXISTS swipe_image_url VARCHAR(500);

UPDATE tour_spots SET swipe_image_url = 'https://bujirun-storage.s3.ap-northeast-2.amazonaws.com/spots/swipe/' || content_id || CASE WHEN content_id = '2456767' THEN '.png' ELSE '.jpg' END
WHERE content_id IN (
                     '1918263',  -- 누리마루 APEC하우스
                     '2456767',  -- F1963
                     '2456837',  -- 부산 영화의 전당
                     '126078',   -- 광안리해수욕장
                     '127004',   -- 해운대 관광특구
                     '2782765',  -- 곰내연밭
                     '987810',   -- 해운대 동백섬
                     '2385686',  -- 아홉산숲
                     '2672393',  -- 해운대 블루라인파크
                     '2784112',  -- 송정서핑학교
                     '2504464',  -- 부산 송도해상케이블카
                     '229912'    -- 씨라이프부산아쿠아리움
    );
