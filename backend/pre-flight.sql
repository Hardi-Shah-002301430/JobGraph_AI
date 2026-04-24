-- ============================================================
-- JobGraph AI — Pre-flight schema setup
-- Run this once against your 'jobgraph' database before mvn.
-- ============================================================

-- ── 1. Users table (from migration 001) ──────────────────────
CREATE TABLE IF NOT EXISTS users (
    id              BIGSERIAL PRIMARY KEY,
    email           VARCHAR(200)  NOT NULL UNIQUE,
    full_name       VARCHAR(200),
    slack_channel   VARCHAR(100),
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

-- Seed the default user (id=1) used by endpoints when no auth is wired.
INSERT INTO users (id, email, full_name)
VALUES (1, 'default@jobgraph.local', 'Default User')
ON CONFLICT (email) DO NOTHING;

-- Make sure the sequence is past the seeded row so future inserts don't collide.
SELECT setval('users_id_seq', GREATEST((SELECT MAX(id) FROM users), 1));

-- ── 2. resume_profiles: attach to user ───────────────────────
ALTER TABLE resume_profiles
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);

UPDATE resume_profiles SET user_id = 1 WHERE user_id IS NULL;

ALTER TABLE resume_profiles
    ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_resume_user ON resume_profiles(user_id);

-- ── 3. application_tracking: attach to user ──────────────────
ALTER TABLE application_tracking
    ADD COLUMN IF NOT EXISTS user_id BIGINT REFERENCES users(id);

UPDATE application_tracking SET user_id = 1 WHERE user_id IS NULL;

ALTER TABLE application_tracking
    ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_tracking_user ON application_tracking(user_id);

-- ── 4. job_alerts dedup table ────────────────────────────────
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

-- ── 5. jobs.apply_url for the new Adzuna flow ────────────────
ALTER TABLE jobs
    ADD COLUMN IF NOT EXISTS apply_url VARCHAR(1000);

-- ── 6. Clean slate for testing (optional — remove if you want to keep data) ──
-- Uncomment these lines ONLY if you want to wipe previous test data.
--
DELETE FROM job_alerts;
DELETE FROM match_scores;
DELETE FROM application_tracking;
DELETE FROM jobs;
DELETE FROM companies;
DELETE FROM resume_profiles;