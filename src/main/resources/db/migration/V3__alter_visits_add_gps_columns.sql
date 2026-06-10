-- visits 테이블: gps 좌표 및 거리 컬럼 추가, gps_verified → verified 변경
ALTER TABLE visits RENAME COLUMN gps_verified TO verified;
ALTER TABLE visits ADD COLUMN gps_lat       DECIMAL(10, 7) NOT NULL DEFAULT 0;
ALTER TABLE visits ADD COLUMN gps_lng       DECIMAL(10, 7) NOT NULL DEFAULT 0;
ALTER TABLE visits ADD COLUMN distance_meters DOUBLE PRECISION NOT NULL DEFAULT 0;
