-- ============================================================
-- JobGraph AI — Migration: users + multi-resume + alerts
-- Run this ONCE on an existing database.
-- ============================================================

-- ── 1. Users ──────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(200)  NOT NULL UNIQUE,
    full_name       VARCHAR(200),
    slack_channel   VARCHAR(100),                  -- per-user Slack override, optional
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

-- Seed a default user so existing data can be migrated cleanly.
-- Change the email before running if you want.
INSERT INTO users (id, email, full_name)
VALUES (1, 'default@jobgraph.local', 'Default User')
ON CONFLICT (email) DO NOTHING;

-- ── 2. Attach existing resumes to the default user ────────
ALTER TABLE resume_profiles
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);

UPDATE resume_profiles SET user_id = 1 WHERE user_id IS NULL;

ALTER TABLE resume_profiles
    ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_resume_user ON resume_profiles(user_id);

-- ── 3. Attach existing tracking rows to the default user ──
ALTER TABLE application_tracking
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);

UPDATE application_tracking SET user_id = 1 WHERE user_id IS NULL;

ALTER TABLE application_tracking
    ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tracking_user ON application_tracking(user_id);

-- ── 4. Alert dedup table ──────────────────────────────────
-- Purpose: one row per (user, job) that has already been alerted on.
-- Prevents spamming Slack if the matcher re-runs on the same job.
CREATE TABLE IF NOT EXISTS job_alerts (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    job_id          BIGINT        NOT NULL REFERENCES jobs(id),
    top_score       DOUBLE PRECISION NOT NULL,
    resume_count    INTEGER       NOT NULL,
    sent_at         TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (user_id, job_id)
);

CREATE INDEX IF NOT EXISTS idx_alerts_user ON job_alerts(user_id);
