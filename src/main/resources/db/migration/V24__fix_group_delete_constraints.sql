-- V24: groups 삭제(마지막 멤버 나가기 시 그룹 자체 삭제) 시 FK 위반이 발생하지 않도록
-- group_id를 참조하는 나머지 테이블의 ON DELETE 옵션을 정리.

-- 그룹이 사라지면 투표 이력도 의미가 없으므로 함께 삭제 (NOT NULL 컬럼이라 SET NULL 불가)
ALTER TABLE itinerary_vote_sessions
    DROP CONSTRAINT itinerary_vote_sessions_group_id_fkey;

ALTER TABLE itinerary_vote_sessions
    ADD CONSTRAINT itinerary_vote_sessions_group_id_fkey
        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE;

-- 스와이프 기록 자체는 사용자 소유로 보존하고, 그룹 연결만 해제
ALTER TABLE swipe_sessions
    DROP CONSTRAINT swipe_sessions_group_id_fkey;

ALTER TABLE swipe_sessions
    ADD CONSTRAINT swipe_sessions_group_id_fkey
        FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE SET NULL;
