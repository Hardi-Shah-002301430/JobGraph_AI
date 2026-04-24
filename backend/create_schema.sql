-- ============================================================
-- JobGraph AI — Database Schema  (PostgreSQL)
-- ============================================================

-- ── Resume profiles ──
CREATE TABLE IF NOT EXISTS resume_profiles (
    id              BIGSERIAL PRIMARY KEY,
    full_name       VARCHAR(200),
    email           VARCHAR(200),
    phone           VARCHAR(50),
    summary         TEXT,
    raw_text        TEXT           NOT NULL,
    skills          TEXT[],                          -- extracted skill tags
    experience_years INTEGER,
    education_level VARCHAR(50),                     -- e.g. BACHELORS, MASTERS, PHD
    preferred_roles TEXT[],                          -- LLM-suggested role titles
    created_at      TIMESTAMP     NOT NULL DEFAULT now(),
    updated_at      TIMESTAMP     NOT NULL DEFAULT now()
);

-- ── Companies we scrape ──
CREATE TABLE IF NOT EXISTS companies (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(300)  NOT NULL,
    careers_url     VARCHAR(500),
    board_type      VARCHAR(50)   NOT NULL DEFAULT 'ASHBY',  -- ASHBY | GREENHOUSE | LEVER | GENERIC
    logo_url        VARCHAR(500),
    industry        VARCHAR(200),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP     NOT NULL DEFAULT now()
);

-- ── Jobs polled from boards ──
CREATE TABLE IF NOT EXISTS jobs (
    id              BIGSERIAL PRIMARY KEY,
    external_id     VARCHAR(200)  NOT NULL,
    company_id      BIGINT        NOT NULL REFERENCES companies(id),
    title           VARCHAR(300)  NOT NULL,
    location        VARCHAR(300),
    department      VARCHAR(200),
    description     TEXT,
    employment_type VARCHAR(50),                     -- FULL_TIME, CONTRACT, INTERN
    experience_min  INTEGER,
    experience_max  INTEGER,
    posted_at       TIMESTAMP,
    scraped_at      TIMESTAMP     NOT NULL DEFAULT now(),
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    UNIQUE (external_id, company_id)
);

CREATE INDEX idx_jobs_company ON jobs(company_id);
CREATE INDEX idx_jobs_active  ON jobs(active);

-- ── Match scores ──
CREATE TABLE IF NOT EXISTS match_scores (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT        NOT NULL REFERENCES jobs(id),
    resume_id       BIGINT        NOT NULL REFERENCES resume_profiles(id),
    overall_score   DOUBLE PRECISION NOT NULL,       -- 0‒100
    skill_score     DOUBLE PRECISION,
    experience_score DOUBLE PRECISION,
    education_score DOUBLE PRECISION,
    industry_score  DOUBLE PRECISION,
    location_score  DOUBLE PRECISION,
    skill_detail    TEXT,                             -- JSON explanation
    experience_detail TEXT,
    education_detail  TEXT,
    industry_detail   TEXT,
    location_detail   TEXT,
    resume_tips     TEXT,                             -- advisor suggestions
    computed_at     TIMESTAMP     NOT NULL DEFAULT now(),
    UNIQUE (job_id, resume_id)
);

CREATE INDEX idx_match_resume ON match_scores(resume_id);
CREATE INDEX idx_match_score  ON match_scores(overall_score DESC);

-- ── Application tracking ──
CREATE TYPE application_status AS ENUM (
    'BOOKMARKED','APPLIED','PHONE_SCREEN','INTERVIEW',
    'OFFER','ACCEPTED','REJECTED','WITHDRAWN'
);

CREATE TABLE IF NOT EXISTS application_tracking (
    id              BIGSERIAL PRIMARY KEY,
    job_id          BIGINT            NOT NULL REFERENCES jobs(id),
    resume_id       BIGINT            NOT NULL REFERENCES resume_profiles(id),
    status          application_status NOT NULL DEFAULT 'BOOKMARKED',
    notes           TEXT,
    applied_at      TIMESTAMP,
    updated_at      TIMESTAMP         NOT NULL DEFAULT now(),
    UNIQUE (job_id, resume_id)
);

-- ── Email classifications ──
CREATE TABLE IF NOT EXISTS email_classifications (
    id              BIGSERIAL PRIMARY KEY,
    tracking_id     BIGINT        REFERENCES application_tracking(id),
    subject         VARCHAR(500),
    sender          VARCHAR(300),
    body_snippet    TEXT,
    classification  VARCHAR(50),                     -- e.g. REJECTION, INTERVIEW_INVITE, OFFER
    confidence      DOUBLE PRECISION,
    classified_at   TIMESTAMP     NOT NULL DEFAULT now()
);
