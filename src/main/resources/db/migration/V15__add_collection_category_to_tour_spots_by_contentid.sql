-- V15__add_collection_category_to_tour_spots_by_contentid.sql

-- 컬럼이 이미 있으면 스킵 (V13에서 이미 추가됐을 수 있음)
ALTER TABLE tour_spots ADD COLUMN IF NOT EXISTS collection_category VARCHAR(10);

-- ============ 바다 (8) ============
UPDATE tour_spots SET is_collection = true, collection_category = '바다'
WHERE content_id IN (
                     127004,   -- 해운대 관광특구
                     2607943,  -- 청사포 다릿돌전망대
                     126078,   -- 광안리해수욕장
                     2606221,  -- 영도 흰여울해안터널
                     252561,   -- 절영해안산책로
                     126122,   -- 부산 송도해수욕장
                     2785289,  -- 감지해변
                     2870289   -- 오륙도해맞이공원
    );

-- ============ 자연 (13) ============
UPDATE tour_spots SET is_collection = true, collection_category = '자연'
WHERE content_id IN (
                     987810,   -- 해운대 동백섬
                     2875837,  -- 달맞이동산
                     2736392,  -- 청학수변공원
                     2385686,  -- 아홉산숲
                     2733472,  -- 황령산 전망대
                     1608751,  -- 태종사
                     2733473,  -- 아미산 전망대
                     129156,   -- 가덕도 등대
                     2661475,  -- 회동수원지
                     2782765,  -- 곰내연밭
                     128830,   -- 금강식물원
                     2782782,  -- 해운대수목원
                     2656194   -- 친환경 스카이웨이 전망대(이바구길)
    );

-- ============ 문화 (24) ============
UPDATE tour_spots SET is_collection = true, collection_category = '문화'
WHERE content_id IN (
                     2350092,  -- 더베이101
                     2775559,  -- 해광사
                     1918263,  -- 누리마루 APEC하우스
                     2456767,  -- F1963
                     2617724,  -- 마린시티
                     2946508,  -- 부산 영화의 거리
                     130166,   -- 부산시립미술관
                     2994179,  -- 전포공구길
                     132191,   -- 국제시장
                     1277679,  -- 부산타워
                     132190,   -- 부산 자갈치시장
                     2708108,  -- 범일 이중섭거리
                     2554070,  -- 깡깡이 예술마을
                     127925,   -- 광안리해변 테마거리
                     2788392,  -- 한국신발관
                     2456837,  -- 부산 영화의 전당
                     2945389,  -- UN조각공원
                     1878218,  -- 부평깡통시장
                     1303784,  -- 남천 해변시장
                     1304431,  -- 해운대 로데오거리
                     2869279,  -- 수영팔도시장
                     2869135,  -- 초량시장
                     1250885,  -- 구포시장
                     995455    -- 해운대시장
    );

-- ============ 체험 (15) ============
UPDATE tour_spots SET is_collection = true, collection_category = '체험'
WHERE content_id IN (
                     2672393,  -- 해운대 블루라인파크
                     2815627,  -- 롯데월드 어드벤처 부산
                     3064821,  -- 자갈치 크루즈
                     2554068,  -- 부산영화체험박물관
                     769761,   -- 다대포 꿈의 낙조분수
                     2504464,  -- 부산 송도해상케이블카
                     2470024,  -- 삼진어묵 체험·역사과학관
                     2456224,  -- 렛츠런파크 부산경남
                     3017112,  -- 동래읍성 임진왜란 역사관
                     2784112,  -- 송정서핑학교
                     128564,   -- 대항 어촌체험 휴양마을
                     2991028,  -- 광안리 해양레포츠센터
                     2756707,  -- 삼락수상레포츠타운
                     229912,   -- 씨라이프부산아쿠아리움
                     1942337   -- 168계단
    );

-- CHECK 제약은 V13에서 이미 추가됐다면 재실행 시 에러 나므로 존재 여부 확인 후 추가
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_collection_category'
    ) THEN
ALTER TABLE tour_spots ADD CONSTRAINT chk_collection_category
    CHECK (
        (is_collection = true AND collection_category IN ('바다', '자연', '문화', '체험'))
            OR (is_collection = false AND collection_category IS NULL)
        );
END IF;
END $$;
