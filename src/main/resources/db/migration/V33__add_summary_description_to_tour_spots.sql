-- V33: TourAPI 자체 개요(overview)로 채워진 소개글(description)을 OpenAI로 재요약할 때,
-- 부산명소정보 때와 달리 원문 description은 보존하고 요약본만 별도 컬럼에 저장하기 위해 추가.
-- (부산명소정보 쪽은 이미 description 자체를 요약본으로 덮어쓴 상태라 여기서 손대지 않음)

ALTER TABLE tour_spots
    ADD COLUMN summary_description TEXT;
