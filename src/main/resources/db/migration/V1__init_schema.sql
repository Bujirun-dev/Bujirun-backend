-- =============================================================
-- 부지런 (Bujirun) — Database Schema
-- PostgreSQL 18
-- =============================================================


-- =============================================================
-- [1] User Domain
-- =============================================================

CREATE TABLE users (
                       id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                       nickname        VARCHAR(50) NOT NULL,
                       email           VARCHAR(255),
                       auth_provider   VARCHAR(20) NOT NULL CHECK (auth_provider IN ('kakao', 'local')),
                       provider_id     VARCHAR(255),               -- 카카오 고유 ID (kakao only)
                       password_hash   VARCHAR(255),               -- 자체 회원가입 (local only)
                       created_at      TIMESTAMP   NOT NULL DEFAULT now(),
                       updated_at      TIMESTAMP   NOT NULL DEFAULT now(),
                       UNIQUE (auth_provider, provider_id)
);


-- =============================================================
-- [2] Tourism Domain  (TourAPI 캐싱 — 배치 동기화)
-- =============================================================

CREATE TABLE sigungu (
                         id      SERIAL      PRIMARY KEY,
                         code    VARCHAR(10) NOT NULL UNIQUE,
                         name    VARCHAR(50) NOT NULL
);

CREATE TABLE tour_spots (
                            id              UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
                            content_id      VARCHAR(50)     NOT NULL UNIQUE,
                            name            VARCHAR(200)    NOT NULL,
                            category        VARCHAR(50),
                            sigungu_id      INT             REFERENCES sigungu(id),
                            lat             DECIMAL(10, 7),
                            lng             DECIMAL(10, 7),
                            address         VARCHAR(300),
                            thumbnail_url   TEXT,
                            operating_hours TEXT,
                            synced_at       TIMESTAMP       NOT NULL DEFAULT now()
);

CREATE TABLE tour_spot_tags (
                                id      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                spot_id UUID        NOT NULL REFERENCES tour_spots(id) ON DELETE CASCADE,
                                tag     VARCHAR(50) NOT NULL
);


-- =============================================================
-- [3] Preference Domain  (일시적 — AI 호출 전까지만 의미 있음)
-- =============================================================

CREATE TABLE swipe_sessions (
                                id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                user_id     UUID        NOT NULL REFERENCES users(id),
                                status      VARCHAR(20) NOT NULL DEFAULT 'active'
                                    CHECK (status IN ('active', 'completed', 'deleted')),
                                created_at  TIMESTAMP   NOT NULL DEFAULT now(),
                                deleted_at  TIMESTAMP
);


-- =============================================================
-- [4] Itinerary Domain  (핵심)
-- =============================================================

CREATE TABLE itineraries (
                             id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                             user_id     UUID        NOT NULL REFERENCES users(id),
                             session_id  UUID        REFERENCES swipe_sessions(id),
                             plan_type   CHAR(1)     NOT NULL CHECK (plan_type IN ('A', 'B', 'C')),
                             status      VARCHAR(20) NOT NULL DEFAULT 'draft'
                                 CHECK (status IN ('draft', 'confirmed')),
                             title       VARCHAR(200),
                             created_at  TIMESTAMP   NOT NULL DEFAULT now(),
                             updated_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE itinerary_days (
                                id              UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
                                itinerary_id    UUID    NOT NULL REFERENCES itineraries(id) ON DELETE CASCADE,
                                day_number      INT     NOT NULL,
                                date            DATE,
                                UNIQUE (itinerary_id, day_number)
);

CREATE TABLE itinerary_items (
                                 id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                                 day_id          UUID        NOT NULL REFERENCES itinerary_days(id) ON DELETE CASCADE,
                                 spot_id         UUID        NOT NULL REFERENCES tour_spots(id),
                                 order_index     INT         NOT NULL,
                                 arrival_time    TIME,
                                 duration_min    INT,
                                 travel_mode     VARCHAR(20) CHECK (travel_mode IN ('walk', 'transit', 'taxi')),
                                 travel_time_min INT,
                                 memo            TEXT
);


-- =============================================================
-- [5] Collection Domain  (도감)
-- =============================================================

CREATE TABLE collection_entries (
                                    user_id         UUID        NOT NULL REFERENCES users(id),
                                    spot_id         UUID        NOT NULL REFERENCES tour_spots(id),
                                    collected       BOOLEAN     NOT NULL DEFAULT false,
                                    collected_at    TIMESTAMP,
                                    PRIMARY KEY (user_id, spot_id)
);

CREATE TABLE visits (
                        id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                        user_id         UUID        NOT NULL REFERENCES users(id),
                        spot_id         UUID        NOT NULL REFERENCES tour_spots(id),
                        gps_verified    BOOLEAN     NOT NULL DEFAULT false,
                        visited_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE visit_photos (
                              id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                              visit_id    UUID        NOT NULL REFERENCES visits(id) ON DELETE CASCADE,
                              s3_key      TEXT        NOT NULL,
                              created_at  TIMESTAMP   NOT NULL DEFAULT now()
);


-- =============================================================
-- [6] Log/Share Domain
-- =============================================================

CREATE TABLE travel_logs (
                             id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
                             user_id         UUID        NOT NULL REFERENCES users(id),
                             itinerary_id    UUID        NOT NULL REFERENCES itineraries(id),
                             title           VARCHAR(200) NOT NULL,
                             shared          BOOLEAN     NOT NULL DEFAULT false,
                             likes_count     INT         NOT NULL DEFAULT 0,
                             created_at      TIMESTAMP   NOT NULL DEFAULT now(),
                             updated_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE log_likes (
                           user_id     UUID        NOT NULL REFERENCES users(id),
                           log_id      UUID        NOT NULL REFERENCES travel_logs(id) ON DELETE CASCADE,
                           created_at  TIMESTAMP   NOT NULL DEFAULT now(),
                           PRIMARY KEY (user_id, log_id)
);

CREATE TABLE log_imports (
                             id                      UUID    PRIMARY KEY DEFAULT gen_random_uuid(),
                             user_id                 UUID    NOT NULL REFERENCES users(id),
                             log_id                  UUID    NOT NULL REFERENCES travel_logs(id),
                             imported_itinerary_id   UUID    REFERENCES itineraries(id),
                             imported_at             TIMESTAMP NOT NULL DEFAULT now()
);


-- =============================================================
-- Indexes
-- =============================================================

-- [Tourism]
CREATE INDEX idx_tour_spots_sigungu    ON tour_spots(sigungu_id);
CREATE INDEX idx_tour_spots_category   ON tour_spots(category);
CREATE INDEX idx_tour_spots_latlng     ON tour_spots(lat, lng);
CREATE INDEX idx_spot_tags_spot        ON tour_spot_tags(spot_id);
CREATE INDEX idx_spot_tags_tag         ON tour_spot_tags(tag);

-- [Preference]
CREATE INDEX idx_swipe_sessions_user   ON swipe_sessions(user_id, status);

-- [Itinerary]
CREATE INDEX idx_itineraries_user      ON itineraries(user_id);
CREATE INDEX idx_itineraries_session   ON itineraries(session_id);
CREATE INDEX idx_itin_items_day        ON itinerary_items(day_id, order_index);

-- [Collection]
CREATE INDEX idx_collection_spot       ON collection_entries(spot_id);
CREATE INDEX idx_visits_user           ON visits(user_id);
CREATE INDEX idx_visits_spot_time      ON visits(spot_id, visited_at);

-- [Log/Share]
CREATE INDEX idx_travel_logs_user      ON travel_logs(user_id);
CREATE INDEX idx_travel_logs_shared    ON travel_logs(shared, created_at DESC);
CREATE INDEX idx_log_likes_log         ON log_likes(log_id);