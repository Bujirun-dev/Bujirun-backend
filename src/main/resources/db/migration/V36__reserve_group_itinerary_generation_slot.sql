-- V36: 그룹 일정 생성 시 "생성 중(generating)" 상태를 먼저 선점하도록 확장한다.
-- 선점에 실패한 요청은 이 자리가 'voting'으로 바뀔 때까지 폴링해서 같은 결과를 받는다.

ALTER TABLE itinerary_vote_sessions
    DROP CONSTRAINT itinerary_vote_sessions_status_check;

ALTER TABLE itinerary_vote_sessions
    ADD CONSTRAINT itinerary_vote_sessions_status_check
        CHECK (status IN ('generating', 'voting', 'confirmed'));

-- 생성 중(generating)에는 아직 AI 응답이 없으므로 plans_json이 비어있을 수 있다.
ALTER TABLE itinerary_vote_sessions
    ALTER COLUMN plans_json DROP NOT NULL;

DROP INDEX itinerary_vote_sessions_group_id_voting_unique;

CREATE UNIQUE INDEX itinerary_vote_sessions_group_id_active_unique
    ON itinerary_vote_sessions(group_id) WHERE status IN ('generating', 'voting');
