-- V17__soft_delete_users.sql
-- 회원탈퇴 Soft Delete
-- 개인 식별 정보만 null 처리하고 여행 데이터는 유지

ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP;

-- 익명화를 위해 개인정보 컬럼 NOT NULL 제약 해제
ALTER TABLE users ALTER COLUMN nickname DROP NOT NULL;
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
ALTER TABLE users ALTER COLUMN provider_id DROP NOT NULL;