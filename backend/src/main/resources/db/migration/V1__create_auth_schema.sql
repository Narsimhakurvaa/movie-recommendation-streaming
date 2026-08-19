-- ---------------------------------------------------------------------------
-- V1 - Identity, authentication and authorisation schema.
-- ---------------------------------------------------------------------------

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(32) NOT NULL,
    description VARCHAR(255),
    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE users (
    id                   BIGSERIAL PRIMARY KEY,
    email                VARCHAR(320) NOT NULL,
    -- BCrypt digest ($2a$...), never a plaintext or reversible value.
    password_hash        VARCHAR(100) NOT NULL,
    display_name         VARCHAR(80)  NOT NULL,
    avatar_url           VARCHAR(512),
    biography            VARCHAR(500),
    enabled              BOOLEAN      NOT NULL DEFAULT TRUE,
    email_verified       BOOLEAN      NOT NULL DEFAULT FALSE,
    onboarding_completed BOOLEAN      NOT NULL DEFAULT FALSE,
    last_login_at        TIMESTAMPTZ,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    -- Case-insensitive uniqueness is enforced by the index below.
    CONSTRAINT uq_users_email UNIQUE (email)
);

-- Emails are matched case-insensitively during login.
CREATE UNIQUE INDEX idx_users_email_lower ON users (LOWER(email));
CREATE INDEX idx_users_enabled ON users (enabled);
CREATE INDEX idx_users_created_at ON users (created_at DESC);

CREATE TABLE user_roles (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id) ON DELETE CASCADE
);

CREATE INDEX idx_user_roles_role ON user_roles (role_id);

-- Refresh tokens are stored ONLY as SHA-256 digests so that a database
-- disclosure cannot be replayed against the authentication endpoints.
CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    token_hash  CHAR(64)     NOT NULL,
    -- Rotation chain: the token that superseded this one.
    replaced_by CHAR(64),
    issued_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    expires_at  TIMESTAMPTZ  NOT NULL,
    revoked_at  TIMESTAMPTZ,
    user_agent  VARCHAR(255),
    client_ip   VARCHAR(64),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens (expires_at);

-- Single-use tokens backing password reset and e-mail verification. Stored as
-- digests for the same reason as refresh tokens.
CREATE TABLE account_tokens (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    token_type  VARCHAR(32) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_account_tokens_hash UNIQUE (token_hash),
    CONSTRAINT ck_account_tokens_type CHECK (token_type IN ('PASSWORD_RESET', 'EMAIL_VERIFICATION')),
    CONSTRAINT fk_account_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_account_tokens_user_type ON account_tokens (user_id, token_type);

INSERT INTO roles (name, description) VALUES
    ('ROLE_USER',  'Standard authenticated viewer'),
    ('ROLE_ADMIN', 'Platform administrator with moderation privileges');
