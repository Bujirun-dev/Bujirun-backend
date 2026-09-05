-- V37: 그룹 일정 투표 세션에 시작/종료 시각을 저장한다.
-- 지금까지 startTime/endTime은 어느 DB에도 저장되지 않고 프론트 URL 쿼리 파라미터로만
-- 화면 간에 전달되고 있었다. 초대 링크 생성 시 이 값이 누락되면서 방장이 입력한 시간과
-- 팀원 화면에 표시되는 시간이 달라지는 문제(2026-09-05 발견)가 있었음.
-- AI 생성을 실제로 수행한 요청의 startTime/endTime을 세션에 함께 저장해서, 모든 멤버가
-- 세션 조회 응답으로 같은 값을 받도록 한다.
ALTER TABLE itinerary_vote_sessions
    ADD COLUMN start_time TIME,
    ADD COLUMN end_time TIME;
