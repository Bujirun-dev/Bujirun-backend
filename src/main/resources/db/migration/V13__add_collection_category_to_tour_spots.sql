-- V13__add_collection_category_to_tour_spots.sql

ALTER TABLE tour_spots ADD COLUMN IF NOT EXISTS collection_category VARCHAR(10);

UPDATE tour_spots SET collection_category = '바다' WHERE id IN (
                                                              'e066aa0e-c5d3-4e6d-910e-dbac69ac193c', -- 해운대 관광특구
                                                              '4732bfee-53a0-42e1-8e13-0ffb65fd7cb0', -- 청사포다릿돌전망대
                                                              '58a1d00a-3149-4f57-911c-14c5345ec75f', -- 광안리해수욕장
                                                              '6d1f9b68-8bbe-42d1-a575-2bd863df599c', -- 흰여울해안터널
                                                              '9fe5e698-5f0e-417f-b069-d79deeb62341', -- 절영해안산책로
                                                              '74825925-3532-4bd1-894e-065b62ddd4e5', -- 송도해수욕장
                                                              '15af7d8b-bc17-49e0-8ff7-53eab41d0d5b', -- 감지해변
                                                              'b44ae93c-0ad2-4f05-a818-9d8c88f16df5'  -- 오륙도해맞이공원
    );

UPDATE tour_spots SET collection_category = '자연' WHERE id IN (
                                                              '8ba60b0e-4575-4373-8ded-8398cc0022b5', -- 동백섬
                                                              'a5c8d876-21d2-48db-93a6-e07c2cca81b4', -- 달맞이동산
                                                              '0e872ea2-4b93-4300-a482-981cc9a6a2cd', -- 청학수변공원
                                                              '571c726d-1a1c-4793-a66b-6ad7f69e247f', -- 아홉산숲
                                                              '19a18883-4b33-4cb8-8fbd-dfa801356c8f', -- 황령산전망대
                                                              '3a1ccdb6-40c1-42b1-a37c-c7e111312a35', -- 태종사
                                                              '9c76a2ab-299d-4cf9-aa38-77ac617b541b', -- 아미산전망대
                                                              'df3fe35f-6739-4e8c-aefb-ffcaafa2e4f6', -- 가덕도등대
                                                              '51faf12b-8946-44a5-8d28-c7c929e659ad', -- 회동수원지
                                                              'e3f1afac-651b-4a14-ade9-5d61efd17131', -- 곰내연밭
                                                              'd43452ba-cf51-4f64-ae8e-4fb2b6fd332d', -- 금강식물원
                                                              'ec23b95a-ce26-411f-9ceb-355c2dd76d33', -- 해운대수목원
                                                              'a08a5952-c1ce-49c9-aec6-f855ecd7725a'  -- 친환경스카이웨이전망대
    );

UPDATE tour_spots SET collection_category = '문화' WHERE id IN (
                                                              'ec2790b6-908c-4ed0-98e0-878c3ecf5614', -- 더베이101
                                                              '17737656-40f1-4998-9a2c-9a5534481f2e', -- 해광사
                                                              '39cd9b9d-9472-4df9-982c-172eb3c886a9', -- 누리마루 APEC하우스
                                                              '3efadcb7-16ed-4889-a05e-4324e66bdd96', -- F1963
                                                              'e0b36ec7-a15a-40f9-89d1-7cecf934de9c', -- 마린시티
                                                              '0b39132f-4964-484e-aa78-1e146fae577b', -- 영화의거리
                                                              '3f1ff2ab-e92e-4c9f-9bab-133997f0193c', -- 부산시립미술관
                                                              'c73b6ae3-8641-41af-88e5-0ba69994ba5c', -- 전포공구길
                                                              '7b428ef2-3427-478d-8a6d-d22a08c68937', -- 국제시장
                                                              'af2d9a1a-b1bf-443e-a389-0eaae11026ff', -- 부산타워
                                                              '3dcff139-64fd-481e-afa3-d0d713bd6e73', -- 자갈치시장
                                                              '21929bae-b0a5-4f3d-8162-dad24591240a', -- 범일 이중섭거리
                                                              '435f340d-b256-4fdd-9688-7240023d7b33', -- 깡깡이예술마을
                                                              'f7019011-c852-49b1-9e28-2463782c694a', -- 광안리해변테마거리
                                                              '99a19382-cacd-4083-a57d-ed35e7607918', -- 한국신발관
                                                              'b7023741-7267-4241-8dd5-c36bf43ff945', -- 영화의전당
                                                              'ec1d875c-dad9-43fe-88ab-da3247e4d335', -- UN조각공원
                                                              '5ca67fb0-5f88-40e2-b53d-a1cf4ab4bb7b', -- 부평깡통시장
                                                              '5c813050-2eac-4ef7-8bd2-9f643bf824e7', -- 해운대시장
                                                              'b091614c-cadf-4a06-b50e-8cf64a7989eb', -- 남천 해변시장
                                                              '9cc6cdde-3b84-45c3-b6cf-b02bf330c13f', -- 해운대 로데오
                                                              '8939756d-ff43-4306-b4ac-5d313e5e2c7d', -- 수영팔도시장
                                                              '12a73e8b-5afb-437f-be2c-26d6504270c0', -- 초량시장
                                                              '088b8f88-cb28-4559-bccd-19ad12a3e60f'  -- 구포시장
    );

UPDATE tour_spots SET collection_category = '체험' WHERE id IN (
                                                              'ef06cc64-72ab-4fc1-b5ef-2a0a98905db8', -- 블루라인파크
                                                              '84490bca-aa30-4cbc-aed6-16447c77b650', -- 롯데월드 부산
                                                              '4e256de0-b43c-4eeb-b92c-346aacb6bdf6', -- 자갈치크루즈
                                                              'b7798ab5-0524-4084-b5c7-cfcecda8ee21', -- 부산영화체험박물관
                                                              '2b055600-fb05-40f2-8caa-60006238d2bf', -- 다대포 꿈의 낙조분수
                                                              'f3c9d157-4620-4113-a376-efd49e74370b', -- 송도해상케이블카
                                                              '3a333d30-61ff-4043-ba9b-5b9337ddcf1e', -- 삼진어묵체험관
                                                              '099aab0c-ac8b-4bbe-aa30-0a213ef5aa5e', -- 렛츠런파크 부산경남
                                                              '761052ee-13d4-44ca-a628-6ae202421357', -- 동래읍성 임진왜란 역사관
                                                              'f7f1340e-56d7-462c-a054-d09c7cd8e17f', -- 송정서핑학교
                                                              'c52e65b7-4bb6-4367-b782-96ed5441e8f6', -- 대항 어촌체험마을
                                                              '7f7c7824-6385-4bb4-a98e-6186ff16e6bb', -- 광안리해양레포츠센터
                                                              '91a720a9-7ef2-4176-9851-0a5a30f0a39f', -- 삼락수상레포츠타운
                                                              '11333244-a9b1-4bf9-89ed-80e3e824a09a', -- 씨라이프부산아쿠아리움
                                                              '0c4ac055-f891-433c-95b2-976fb0fba2b4'  -- 168계단
    );

ALTER TABLE tour_spots ADD CONSTRAINT chk_collection_category
    CHECK (
        (is_collection = true AND collection_category IN ('바다', '자연', '문화', '체험'))
            OR (is_collection = false AND collection_category IS NULL)
        );