CREATE TABLE group_flow_timers (
    group_id UUID NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    phase VARCHAR(20) NOT NULL CHECK (phase IN ('waiting', 'vote-waiting')),
    scope_id UUID NOT NULL,
    deadline_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, phase, scope_id)
);
