-- ---------------------------------------------------------------------------
-- V3 - User interaction signals: preferences, ratings, reviews, watchlist,
--      watch history and recommendation logs.
-- ---------------------------------------------------------------------------

CREATE TABLE user_preferences (
    user_id              BIGINT PRIMARY KEY,
    preferred_languages  VARCHAR(128),
    include_adult        BOOLEAN     NOT NULL DEFAULT FALSE,
    -- Minimum external rating a title must hold to enter recommendations.
    minimum_rating       NUMERIC(4, 2) NOT NULL DEFAULT 0,
    preferred_decade_from SMALLINT,
    preferred_decade_to   SMALLINT,
    theme                VARCHAR(16) NOT NULL DEFAULT 'system',
    email_notifications  BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT fk_user_preferences_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT ck_user_preferences_theme CHECK (theme IN ('light', 'dark', 'system'))
);

-- Explicit "favourite genre" onboarding signal. `weight` lets the UI express
-- ordering (first pick counts more) without a second table.
CREATE TABLE user_favourite_genres (
    user_id  BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    weight   NUMERIC(4, 3) NOT NULL DEFAULT 1.0,
    CONSTRAINT pk_user_favourite_genres PRIMARY KEY (user_id, genre_id),
    CONSTRAINT fk_ufg_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_ufg_genre FOREIGN KEY (genre_id) REFERENCES genres (id) ON DELETE CASCADE,
    CONSTRAINT ck_ufg_weight CHECK (weight > 0 AND weight <= 1)
);

CREATE TABLE user_favourite_movies (
    user_id  BIGINT      NOT NULL,
    movie_id BIGINT      NOT NULL,
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_user_favourite_movies PRIMARY KEY (user_id, movie_id),
    CONSTRAINT fk_ufm_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_ufm_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

-- One rating per (user, movie) - enforced by the primary key rather than by
-- application logic, so concurrent submissions cannot create duplicates.
CREATE TABLE ratings (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    movie_id   BIGINT      NOT NULL,
    score      SMALLINT    NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_ratings_user_movie UNIQUE (user_id, movie_id),
    CONSTRAINT ck_ratings_score CHECK (score BETWEEN 1 AND 5),
    CONSTRAINT fk_ratings_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_ratings_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

-- Drives "users who rated this movie" during collaborative filtering.
CREATE INDEX idx_ratings_movie_score ON ratings (movie_id, score);
CREATE INDEX idx_ratings_user        ON ratings (user_id, updated_at DESC);

CREATE TABLE reviews (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL,
    movie_id     BIGINT       NOT NULL,
    title        VARCHAR(160),
    body         TEXT         NOT NULL,
    contains_spoilers BOOLEAN NOT NULL DEFAULT FALSE,
    status       VARCHAR(16)  NOT NULL DEFAULT 'PUBLISHED',
    moderation_note VARCHAR(255),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_reviews_user_movie UNIQUE (user_id, movie_id),
    CONSTRAINT ck_reviews_status CHECK (status IN ('PUBLISHED', 'HIDDEN', 'FLAGGED')),
    CONSTRAINT fk_reviews_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_reviews_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

CREATE INDEX idx_reviews_movie_status ON reviews (movie_id, status, created_at DESC);
CREATE INDEX idx_reviews_user         ON reviews (user_id, created_at DESC);

CREATE TABLE watchlist_items (
    id       BIGSERIAL PRIMARY KEY,
    user_id  BIGINT      NOT NULL,
    movie_id BIGINT      NOT NULL,
    note     VARCHAR(255),
    added_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    -- Duplicate prevention is a database guarantee, not an application check.
    CONSTRAINT uq_watchlist_user_movie UNIQUE (user_id, movie_id),
    CONSTRAINT fk_watchlist_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_watchlist_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

CREATE INDEX idx_watchlist_user_added ON watchlist_items (user_id, added_at DESC);

-- Append-only interaction log. Deliberately a single table rather than an
-- event-sourcing pipeline: the recommendation engine only needs aggregate
-- counts per (user, movie, type), which a covering index answers cheaply.
CREATE TABLE watch_history (
    id               BIGSERIAL PRIMARY KEY,
    user_id          BIGINT      NOT NULL,
    movie_id         BIGINT      NOT NULL,
    interaction_type VARCHAR(32) NOT NULL,
    progress_percent SMALLINT,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_watch_history_type CHECK (interaction_type IN (
        'VIEWED_DETAILS', 'WATCHED_TRAILER', 'STARTED_WATCHING',
        'COMPLETED', 'ADDED_TO_WATCHLIST', 'RATED')),
    CONSTRAINT ck_watch_history_progress CHECK (progress_percent IS NULL OR progress_percent BETWEEN 0 AND 100),
    CONSTRAINT fk_watch_history_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_watch_history_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

CREATE INDEX idx_watch_history_user_time  ON watch_history (user_id, occurred_at DESC);
CREATE INDEX idx_watch_history_user_movie ON watch_history (user_id, movie_id, interaction_type);
CREATE INDEX idx_watch_history_movie      ON watch_history (movie_id, interaction_type);

-- Persisted so that "recommendation history" is a real feature and so that
-- served recommendations can be evaluated offline (click-through analysis).
CREATE TABLE recommendation_logs (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT      NOT NULL,
    movie_id            BIGINT      NOT NULL,
    recommendation_type VARCHAR(32) NOT NULL,
    score               NUMERIC(6, 4) NOT NULL,
    reason              VARCHAR(255),
    generated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_recommendation_logs_type CHECK (recommendation_type IN (
        'HYBRID', 'CONTENT_BASED', 'COLLABORATIVE', 'POPULARITY', 'COLD_START', 'SIMILAR')),
    CONSTRAINT fk_reclog_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_reclog_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE
);

CREATE INDEX idx_reclog_user_time ON recommendation_logs (user_id, generated_at DESC);
