-- V4__add_group_and_swipe_results.sql

-- =============================================================
-- [1] Group Domain
-- =============================================================

CREATE TABLE groups (
                        id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                        name        VARCHAR(100),
                        invite_code VARCHAR(20) NOT NULL UNIQUE,
                        created_by  UUID        NOT NULL REFERENCES users(id),
                        created_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE group_members (
                               group_id    UUID        NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
                               user_id     UUID        NOT NULL REFERENCES users(id),
                               joined_at   TIMESTAMP   NOT NULL DEFAULT now(),
                               PRIMARY KEY (group_id, user_id)
);

-- =============================================================
-- [2] Swipe Results (그룹 취합용)
-- =============================================================

CREATE TABLE swipe_results (
                               id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                               session_id  UUID        NOT NULL REFERENCES swipe_sessions(id) ON DELETE CASCADE,
                               spot_id     UUID        NOT NULL REFERENCES tour_spots(id),
                               liked       BOOLEAN     NOT NULL,
                               swiped_at   TIMESTAMP   NOT NULL DEFAULT now()
);

-- =============================================================
-- [3] 기존 테이블 수정
-- =============================================================

ALTER TABLE swipe_sessions ADD COLUMN group_id UUID REFERENCES groups(id);
ALTER TABLE itineraries    ADD COLUMN group_id UUID REFERENCES groups(id);

-- =============================================================
-- Indexes
-- =============================================================

CREATE INDEX idx_groups_invite_code      ON groups(invite_code);
CREATE INDEX idx_group_members_group     ON group_members(group_id);
CREATE INDEX idx_group_members_user      ON group_members(user_id);
CREATE INDEX idx_swipe_results_session   ON swipe_results(session_id);
CREATE INDEX idx_swipe_sessions_group    ON swipe_sessions(group_id);
CREATE INDEX idx_itineraries_group       ON itineraries(group_id);