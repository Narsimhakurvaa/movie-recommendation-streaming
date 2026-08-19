-- ---------------------------------------------------------------------------
-- V2 - Movie catalogue: movies, genres, keywords and credits.
-- ---------------------------------------------------------------------------

CREATE TABLE genres (
    id      BIGSERIAL PRIMARY KEY,
    -- Stable identifier from the external provider (nullable for local genres).
    tmdb_id INTEGER,
    name    VARCHAR(64) NOT NULL,
    slug    VARCHAR(64) NOT NULL,
    CONSTRAINT uq_genres_name UNIQUE (name),
    CONSTRAINT uq_genres_slug UNIQUE (slug),
    CONSTRAINT uq_genres_tmdb UNIQUE (tmdb_id)
);

CREATE TABLE movies (
    id                  BIGSERIAL PRIMARY KEY,
    tmdb_id             INTEGER,
    imdb_id             VARCHAR(16),
    title               VARCHAR(255) NOT NULL,
    original_title      VARCHAR(255),
    slug                VARCHAR(280) NOT NULL,
    tagline             VARCHAR(300),
    overview            TEXT,
    release_date        DATE,
    runtime_minutes     INTEGER,
    original_language   VARCHAR(12),
    origin_country      VARCHAR(64),
    status              VARCHAR(32) NOT NULL DEFAULT 'RELEASED',
    poster_url          VARCHAR(512),
    backdrop_url        VARCHAR(512),
    trailer_url         VARCHAR(512),
    homepage_url        VARCHAR(512),
    -- Ratings sourced from the external provider.
    external_rating     NUMERIC(4, 2) NOT NULL DEFAULT 0,
    external_vote_count INTEGER       NOT NULL DEFAULT 0,
    -- Ratings aggregated from this platform's own users (maintained by the
    -- rating service so that listing queries never need a correlated COUNT).
    average_rating      NUMERIC(4, 2) NOT NULL DEFAULT 0,
    rating_count        INTEGER       NOT NULL DEFAULT 0,
    popularity          NUMERIC(10, 3) NOT NULL DEFAULT 0,
    budget              BIGINT,
    revenue             BIGINT,
    adult               BOOLEAN     NOT NULL DEFAULT FALSE,
    production_companies VARCHAR(512),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_movies_slug UNIQUE (slug),
    CONSTRAINT uq_movies_tmdb UNIQUE (tmdb_id),
    CONSTRAINT ck_movies_runtime CHECK (runtime_minutes IS NULL OR runtime_minutes > 0),
    CONSTRAINT ck_movies_avg_rating CHECK (average_rating >= 0 AND average_rating <= 5),
    CONSTRAINT ck_movies_ext_rating CHECK (external_rating >= 0 AND external_rating <= 10),
    CONSTRAINT ck_movies_status CHECK (status IN ('RELEASED', 'UPCOMING', 'IN_PRODUCTION', 'CANCELLED'))
);

-- Discovery/sort paths. Each supports a documented query in MovieRepository.
CREATE INDEX idx_movies_popularity     ON movies (popularity DESC);
CREATE INDEX idx_movies_release_date   ON movies (release_date DESC NULLS LAST);
CREATE INDEX idx_movies_average_rating ON movies (average_rating DESC, rating_count DESC);
CREATE INDEX idx_movies_language       ON movies (original_language);
CREATE INDEX idx_movies_adult          ON movies (adult);
-- Case-insensitive prefix/`ILIKE` title search.
CREATE INDEX idx_movies_title_lower    ON movies (LOWER(title));

CREATE TABLE movie_genres (
    movie_id BIGINT NOT NULL,
    genre_id BIGINT NOT NULL,
    CONSTRAINT pk_movie_genres PRIMARY KEY (movie_id, genre_id),
    CONSTRAINT fk_movie_genres_movie FOREIGN KEY (movie_id) REFERENCES movies (id) ON DELETE CASCADE,
    CONSTRAINT fk_movie_genres_genre FOREIGN KEY (genre_id) REFERENCES genres (id) ON DELETE CASCADE
);

-- Reverse lookup: "all movies in genre X".
CREATE INDEX idx_movie_genres_genre ON movie_genres (genre_id);

CREATE TABLE keywords (
    id   BIGSERIAL PRIMARY KEY,
    name VARCHAR(96) NOT NULL,
    CONSTRAINT uq_keywords_name UNIQUE (name)
);

CREATE TABLE movie_keywords (
    movie_id   BIGINT NOT NULL,
    keyword_id BIGINT NOT NULL,
    CONSTRAINT pk_movie_keywords PRIMARY KEY (movie_id, keyword_id),
    CONSTRAINT fk_movie_keywords_movie   FOREIGN KEY (movie_id)   REFERENCES movies (id)   ON DELETE CASCADE,
    CONSTRAINT fk_movie_keywords_keyword FOREIGN KEY (keyword_id) REFERENCES keywords (id) ON DELETE CASCADE
);

CREATE INDEX idx_movie_keywords_keyword ON movie_keywords (keyword_id);

CREATE TABLE people (
    id          BIGSERIAL PRIMARY KEY,
    tmdb_id     INTEGER,
    name        VARCHAR(160) NOT NULL,
    profile_url VARCHAR(512),
    CONSTRAINT uq_people_tmdb UNIQUE (tmdb_id)
);

CREATE INDEX idx_people_name ON people (LOWER(name));

-- A single credits table (with a role discriminator) is used instead of
-- separate cast/crew tables: the columns are identical and every read path
-- filters by movie_id, so splitting them would add joins without any benefit.
CREATE TABLE movie_credits (
    id            BIGSERIAL PRIMARY KEY,
    movie_id      BIGINT      NOT NULL,
    person_id     BIGINT      NOT NULL,
    credit_type   VARCHAR(16) NOT NULL,
    character_name VARCHAR(160),
    job           VARCHAR(96),
    display_order INTEGER     NOT NULL DEFAULT 0,
    CONSTRAINT fk_movie_credits_movie  FOREIGN KEY (movie_id)  REFERENCES movies (id) ON DELETE CASCADE,
    CONSTRAINT fk_movie_credits_person FOREIGN KEY (person_id) REFERENCES people (id) ON DELETE CASCADE,
    CONSTRAINT ck_movie_credits_type CHECK (credit_type IN ('CAST', 'DIRECTOR', 'WRITER', 'PRODUCER'))
);

-- A person may hold several distinct credits on one film (e.g. writer *and*
-- director), so uniqueness spans the nullable job/character columns. Expression
-- indexes are required here because UNIQUE constraints cannot use COALESCE.
CREATE UNIQUE INDEX uq_movie_credits
    ON movie_credits (movie_id, person_id, credit_type, COALESCE(job, ''), COALESCE(character_name, ''));

CREATE INDEX idx_movie_credits_movie_type ON movie_credits (movie_id, credit_type, display_order);
CREATE INDEX idx_movie_credits_person     ON movie_credits (person_id, credit_type);
