-- 코드 전수 조사 결과 안 쓰는 테이블/컬럼 정리 (2026-08-25)
-- 각 대상이 앱 코드 어디에서도 read/write되지 않음을 확인 후 제거함

-- tour_spot_tags: TourAPI 동기화 시 write만 하고 어떤 서비스/응답에서도 read한 적 없는 write-only 테이블
DROP TABLE IF EXISTS tour_spot_tags;

-- tour_spots.content_type_id: 동기화 중 로컬 변수로만 쓰이고, 저장된 값을 다시 읽는 코드가 없음
ALTER TABLE tour_spots DROP COLUMN IF EXISTS content_type_id;

-- users.password_hash: 로컬(자체) 회원가입/로그인 플로우 자체가 구현된 적이 없어 항상 NULL
ALTER TABLE users DROP COLUMN IF EXISTS password_hash;

-- swipe_sessions.deleted_at: 세터/빌더조차 없어 항상 NULL. 소프트삭제는 status 컬럼으로 하려던
-- 설계로 보이나 'deleted' 상태값도 실제로는 세팅되는 곳이 없음(둘 다 미구현 상태로 남아있던 컬럼)
ALTER TABLE swipe_sessions DROP COLUMN IF EXISTS deleted_at;
