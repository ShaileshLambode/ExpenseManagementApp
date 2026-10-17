-- =====================================================================
-- 0004  Authentication tables (schema v0.2 -> v0.3)
--
-- Design (ARCHITECTURE.md §9):
--   * Login by e-mail one-time code, or by a Google/Apple identity.
--   * A SESSION is one device's chain of rotating refresh tokens
--     (every token in the chain shares session_id). There is no separate
--     sessions table.
--   * Secrets are never stored: only SHA-256 hashes (32 bytes) of the
--     one-time code and of the refresh token.
--   * A refresh token can only belong to a device of the SAME user
--     (composite foreign key).
-- Rotation and reuse detection are application logic (Day 4); the
-- columns used_at / replaced_by / revoked_* record what happened.
-- =====================================================================

-- Social identities (e-mail login uses users.email, so it needs no row here)
CREATE TABLE auth_identities (
  id                uuid        PRIMARY KEY DEFAULT uuidv7(),
  user_id           uuid        NOT NULL REFERENCES users (id),
  provider          text        NOT NULL CHECK (provider IN ('google', 'apple')),
  provider_subject  text        NOT NULL CHECK (length(provider_subject) BETWEEN 1 AND 255),
  created_at        timestamptz NOT NULL DEFAULT now(),
  last_login_at     timestamptz,
  CONSTRAINT auth_identities_provider_subject_uq UNIQUE (provider, provider_subject)
);
CREATE INDEX auth_identities_user_idx ON auth_identities (user_id);

-- One-time e-mail login codes (hash only; lower-case e-mail)
CREATE TABLE email_login_codes (
  id            uuid        PRIMARY KEY DEFAULT uuidv7(),
  email         text        NOT NULL CHECK (email = lower(email) AND position('@' in email) > 1),
  code_hash     bytea       NOT NULL CHECK (octet_length(code_hash) = 32),
  created_at    timestamptz NOT NULL DEFAULT now(),
  expires_at    timestamptz NOT NULL,
  attempts      smallint    NOT NULL DEFAULT 0 CHECK (attempts >= 0),
  max_attempts  smallint    NOT NULL DEFAULT 5 CHECK (max_attempts BETWEEN 1 AND 10),
  consumed_at   timestamptz,
  CONSTRAINT email_login_codes_expiry_after_creation CHECK (expires_at > created_at),
  CONSTRAINT email_login_codes_attempts_within_max   CHECK (attempts <= max_attempts)
);
CREATE INDEX email_login_codes_email_idx ON email_login_codes (email, created_at DESC);

-- Devices
CREATE TABLE devices (
  id            uuid        PRIMARY KEY DEFAULT uuidv7(),
  user_id       uuid        NOT NULL REFERENCES users (id),
  platform      text        NOT NULL CHECK (platform IN ('android', 'ios', 'web')),
  name          text        NOT NULL DEFAULT '' CHECK (length(name) <= 80),
  created_at    timestamptz NOT NULL DEFAULT now(),
  last_seen_at  timestamptz NOT NULL DEFAULT now(),
  revoked_at    timestamptz,
  CONSTRAINT devices_user_id_uq UNIQUE (user_id, id)    -- target of the composite FK below
);
CREATE INDEX devices_user_idx ON devices (user_id) WHERE revoked_at IS NULL;

-- Refresh tokens (rotating; hash only)
CREATE TABLE refresh_tokens (
  id              uuid        PRIMARY KEY DEFAULT uuidv7(),
  user_id         uuid        NOT NULL,
  device_id       uuid        NOT NULL,
  session_id      uuid        NOT NULL,
  token_hash      bytea       NOT NULL CHECK (octet_length(token_hash) = 32),
  issued_at       timestamptz NOT NULL DEFAULT now(),
  expires_at      timestamptz NOT NULL,
  used_at         timestamptz,
  replaced_by     uuid        REFERENCES refresh_tokens (id),
  revoked_at      timestamptz,
  revoked_reason  text        CHECK (revoked_reason IN ('logout', 'reuse_detected', 'device_revoked', 'expired_cleanup')),
  CONSTRAINT refresh_tokens_hash_uq UNIQUE (token_hash),
  CONSTRAINT refresh_tokens_expiry_after_issue CHECK (expires_at > issued_at),
  CONSTRAINT refresh_tokens_revoked_has_reason CHECK ((revoked_at IS NULL) = (revoked_reason IS NULL)),
  FOREIGN KEY (user_id, device_id) REFERENCES devices (user_id, id)
);
CREATE INDEX refresh_tokens_session_idx ON refresh_tokens (session_id);
CREATE INDEX refresh_tokens_device_idx  ON refresh_tokens (device_id);
CREATE INDEX refresh_tokens_expiry_idx  ON refresh_tokens (expires_at);
