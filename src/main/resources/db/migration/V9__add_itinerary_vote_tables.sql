-- V9__add_itinerary_vote_tables.sql

CREATE TABLE itinerary_vote_sessions (
                                         id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                         group_id        UUID        NOT NULL REFERENCES groups(id),
                                         plans_json      TEXT        NOT NULL,
                                         status          VARCHAR(20) NOT NULL DEFAULT 'voting'
                                             CHECK (status IN ('voting', 'confirmed')),
                                         confirmed_plan  VARCHAR(1),
                                         itinerary_id    UUID        REFERENCES itineraries(id),
                                         created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE itinerary_votes (
                                 id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                 session_id  UUID        NOT NULL REFERENCES itinerary_vote_sessions(id) ON DELETE CASCADE,
                                 user_id     UUID        NOT NULL REFERENCES users(id),
                                 voted_plan  VARCHAR(1)  NOT NULL,
                                 voted_at    TIMESTAMP   NOT NULL DEFAULT now(),
                                 UNIQUE (session_id, user_id)
);

CREATE INDEX idx_vote_sessions_group ON itinerary_vote_sessions(group_id, status);
CREATE INDEX idx_votes_session       ON itinerary_votes(session_id);