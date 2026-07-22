-- V20: itinerary_vote_sessions.itinerary_id FK가 ON DELETE 옵션 없이 걸려있어서
-- 투표로 확정된(itinerary_id가 세팅된) 일정을 삭제하려 하면 FK 위반(500)이 발생하던 문제 수정.
-- 투표 이력은 보존하되, 삭제된 일정에 대한 참조만 끊어지도록 SET NULL로 변경.

ALTER TABLE itinerary_vote_sessions
    DROP CONSTRAINT itinerary_vote_sessions_itinerary_id_fkey;

ALTER TABLE itinerary_vote_sessions
    ADD CONSTRAINT itinerary_vote_sessions_itinerary_id_fkey
        FOREIGN KEY (itinerary_id) REFERENCES itineraries(id) ON DELETE SET NULL;
