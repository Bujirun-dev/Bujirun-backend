ALTER TABLE groups
    ADD COLUMN max_members INTEGER NOT NULL DEFAULT 6;

ALTER TABLE groups
    ADD CONSTRAINT chk_groups_max_members CHECK (max_members BETWEEN 2 AND 6);
