-- V8__add_bookmarks.sql

CREATE TABLE bookmarks (
    user_id         UUID        NOT NULL REFERENCES users(id),
    spot_id         UUID        NOT NULL REFERENCES tour_spots(id),
    bookmarked_at   TIMESTAMP   NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, spot_id)
);

CREATE INDEX idx_bookmarks_spot ON bookmarks(spot_id);
