-- V26: 그룹당 진행 중(voting)인 투표 세션은 하나만 존재하도록 강제.
-- 그룹 멤버 여러 명이 거의 동시에 "투표 시작"을 요청하면 서비스 레이어의
-- findActiveSession() → orElseGet(startVoteSession()) 사이 TOCTOU 경합으로
-- 세션이 여러 개 생겨서 투표가 흩어지는 문제가 있었음(2026-08-06 발견).
-- confirmed 세션은 그룹당 여러 개 누적될 수 있으므로 status='voting'인 행만 제한.
CREATE UNIQUE INDEX itinerary_vote_sessions_group_id_voting_unique
    ON itinerary_vote_sessions(group_id) WHERE status = 'voting';
