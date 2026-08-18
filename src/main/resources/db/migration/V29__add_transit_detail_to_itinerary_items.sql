-- V29: 지하철 등 대중교통 구간이 여러 개(환승 2회 이상)인 경로는 route_type/route_no 같은
-- 대표값 컬럼(V23) 몇 개로는 표현할 수 없다. subPath 배열 전체(도보/버스/지하철 전 구간 +
-- 지하철 구간의 ODsay 배차 시각표/환승 "예정" 정보)를 보존하기 위해 transit_detail을 추가한다.
--
-- 기존 route_type/route_no/start_station_name/end_station_name/start_ars_id(V23)는
-- 첫 대중교통 구간의 대표값으로 계속 채워지며 하위호환을 위해 그대로 유지한다.
ALTER TABLE itinerary_items
    ADD COLUMN transit_detail JSONB;
