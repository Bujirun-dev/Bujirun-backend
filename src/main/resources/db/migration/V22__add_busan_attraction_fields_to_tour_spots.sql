-- V22: 부산광역시_부산명소정보 API(data.go.kr 15063481) 연동을 위해 tour_spots에
-- 소개정보 컬럼 추가. 기존 TourAPI(한국관광공사)는 개요가 짧고 교통정보가 없어서
-- 부산시 API로 보완함. busan_uc_seq는 그 API의 콘텐츠ID(UC_SEQ)로, 재동기화 시
-- 이미 매칭된 스팟을 다시 좌표로 찾지 않고 바로 갱신하기 위한 용도.

ALTER TABLE tour_spots
    ADD COLUMN subtitle        VARCHAR(300),
    ADD COLUMN description     TEXT,
    ADD COLUMN contact         VARCHAR(200),
    ADD COLUMN homepage_url    VARCHAR(200),
    ADD COLUMN transportation  VARCHAR(500),
    ADD COLUMN closed_days     VARCHAR(500),
    ADD COLUMN fee_info        VARCHAR(500),
    ADD COLUMN busan_uc_seq    VARCHAR(20);

CREATE UNIQUE INDEX idx_tour_spots_busan_uc_seq ON tour_spots(busan_uc_seq) WHERE busan_uc_seq IS NOT NULL;
