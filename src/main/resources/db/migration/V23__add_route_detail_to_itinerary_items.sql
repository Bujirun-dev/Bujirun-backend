-- V23: 홈 화면 등에서 실제 버스/지하철 경로 상세(노선번호·정류장명·ARS번호)를 보여주기 위해
-- ItineraryItem에 경로 상세 컬럼을 추가한다.
-- 기존 travel_mode(walk/transit/taxi 요약값), travel_time_min은 그대로 유지하고,
-- 여기에 원본 ODsay 응답값(뭉개지 않은 값)을 함께 저장한다.

-- route_no는 route_type에 따라 의미가 다르다 (OdsayClient.parseTransit() 참고):
--   route_type == '버스'   → route_no = 버스 노선번호 (예: "82")
--   route_type == '지하철' → route_no = 지하철 노선명 (예: "2호선")
--   route_type == '도보'/'택시' → route_no는 null
ALTER TABLE itinerary_items
    ADD COLUMN route_type VARCHAR(20),          -- 원본 이동수단 타입 (예: "버스", "지하철", "도보", "택시")
    ADD COLUMN route_no VARCHAR(20),             -- 버스 노선번호 또는 지하철 노선명 (위 설명 참고)
    ADD COLUMN start_station_name VARCHAR(100),  -- 출발 정류장/역 이름
    ADD COLUMN end_station_name VARCHAR(100),    -- 도착 정류장/역 이름
    ADD COLUMN start_ars_id VARCHAR(20);         -- 버스 정류장 ARS번호 (실시간 도착정보 조회용, 마을버스는 없을 수 있음)