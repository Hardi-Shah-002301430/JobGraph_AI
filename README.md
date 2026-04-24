# JobGraph AI — Complete Setup & Demo Guide

> **AI-Powered Multi-Agent Job Search Platform**  
> Java 21 · Spring Boot 3.4 · Akka Cluster · PostgreSQL · Groq LLM · React + Vite

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Architecture](#2-architecture)
3. [Important Files Reference](#3-important-files-reference)
4. [Prerequisites](#4-prerequisites)
5. [Database Setup](#5-database-setup)
6. [Configuration](#6-configuration)
7. [Running the Application](#7-running-the-application)
8. [Running the Frontend](#8-running-the-frontend)
9. [Demo: Cluster Singleton Failover](#9-demo-cluster-singleton-failover)
10. [Demo: Resume Upload → Immediate Polling](#10-demo-resume-upload--immediate-polling)
11. [Demo: Email Tracker Agent](#11-demo-email-tracker-agent)
12. [API Reference](#12-api-reference)
13. [Troubleshooting](#13-troubleshooting)
14. [Quick Demo Checklist](#14-quick-demo-checklist)

---

## 1. Project Overview

JobGraph AI automates every step of a job search:

| Step | Who does it |
|------|-------------|
| Parse your resume | `ResumeAnalyzerAgent` (LLM) |
| Scrape job boards every 15 min | `PollerAgent` (Adzuna API) |
| Score every job against your resume | `MatchAnalyzerAgent` (LLM, 5 categories) |
| Suggest resume improvements | `ResumeAdvisorAgent` (LLM) |
| Read your email and update application status | `TrackerAgent` (IMAP + LLM) |
| Send Slack alerts for high matches | `NotificationAgent` |
| Orchestrate everything on a timer | `SchedulerAgent` (Akka Cluster Singleton) |

---

## 2. Architecture

```
┌─────────────────────────────────────────────────────────┐
│                   Akka Cluster (3 nodes)                 │
│                                                          │
│  ┌──────────────────────────────────────────────────┐   │
│  │  SchedulerAgent  ← CLUSTER SINGLETON             │   │
│  │  Exactly ONE instance across all nodes.          │   │
│  │  Ticks every N min. If host node dies,           │   │
│  │  migrates automatically to another node.         │   │
│  └──────────────────────────────────────────────────┘   │
│                          │                               │
│         ┌────────────────┼─────────────────┐            │
│         ▼                ▼                 ▼            │
│   PollerAgent      TrackerAgent    NotificationAgent    │
│   (pool of 1)      (local)         ResumeAnalyzerAgent  │
│         │                          MatchAnalyzerAgent   │
│         ▼                          ResumeAdvisorAgent   │
│   MatchAnalyzerAgent                                    │
│   (pool of 1)                                           │
└─────────────────────────────────────────────────────────┘
                          │
              ┌───────────┴───────────┐
              ▼                       ▼
         PostgreSQL              Groq LLM API
         (jobs, scores,          (llama-3.3-70b-versatile)
          tracking, emails)
```

### WebSocket Events — real-time UI updates

| Event | Fired by | Frontend effect |
|-------|----------|-----------------|
| `NEW_JOB` | PollerAgent | Dashboard sidebar flash |
| `MATCH_SCORE` | MatchAnalyzerAgent | Job card score update |
| `STATUS_CHANGE` | TrackerAgent + TrackingController | Tracking row highlight |

---

## 3. Important Files Reference

### Backend — `backend/src/main/java/com/jobgraph/`

| File | One-line purpose |
|------|-----------------|
| `JobGraphApplication.java` | Spring Boot entry point |
| `cluster/ClusterManager.java` | Bootstraps all Akka actors on startup — the wiring hub |
| `cluster/ClusterConstants.java` | Actor name string constants used across the codebase |
| `config/AppProperties.java` | All `jobgraph.*` config properties bound from application.yml |
| `config/AkkaConfig.java` | Loads the Akka `.conf` file and creates the ActorSystem |
| `config/WebSocketConfig.java` | Registers the `/ws/jobs` WebSocket endpoint |
| `agent/SchedulerAgent.java` | **Cluster singleton** — ticks every N minutes, fans out all work |
| `agent/PollerAgent.java` | Calls Adzuna API, saves new jobs, fans out ScoreJob messages |
| `agent/MatchAnalyzerAgent.java` | Scores one job vs one resume across 5 LLM categories |
| `agent/ResumeAnalyzerAgent.java` | Parses uploaded resume via LLM, triggers immediate Adzuna poll |
| `agent/ResumeAdvisorAgent.java` | Generates resume tips for match scores between 60 and 90 |
| `agent/TrackerAgent.java` | Fetches Gmail via IMAP, classifies emails with LLM, updates status |
| `agent/NotificationAgent.java` | Sends Slack webhook when match score exceeds the threshold |
| `service/AdzunaAdapter.java` | HTTP client for the Adzuna Jobs API with full JD scraping |
| `service/LlmService.java` | HTTP client for Groq with exponential retry and 429 handling |
| `service/EmailService.java` | IMAP client — fetches unread Gmail, deduplicates via seen_emails |
| `websocket/JobUpdateHandler.java` | Holds all active WS sessions, broadcasts events to all clients |
| `model/ApplicationTracking.java` | JPA entity for job application tracking rows |
| `model/ApplicationStatus.java` | Enum: BOOKMARKED → APPLIED → PHONE_SCREEN → INTERVIEW → OFFER |

### Backend — Resources (`backend/src/main/resources/`)

| File | Purpose |
|------|---------|
| `application.yml` | All runtime config — DB, LLM, email, Slack, polling intervals |
| `akka-common.conf` | Akka cluster base config — seed nodes, SBR, CBOR serialization |
| `application-node1.conf` | Node 1 overrides: port 2551, roles: scheduler + api |
| `application-node2.conf` | Node 2 overrides: port 2552, roles: scheduler + worker + api |
| `application-node3.conf` | Node 3 overrides: port 2553, roles: worker + api |

### Database SQL (`backend/`)

| File | Purpose |
|------|---------|
| `create_schema.sql` | Full PostgreSQL DDL — run once to create all tables and enums |
| `migration_001_users_and_alerts.sql` | Adds `users` and `job_alerts` tables |
| `pre-flight.sql` | Seed data — creates default user and sample companies |

### Frontend — `frontend/src/`

| File | Purpose |
|------|---------|
| `App.jsx` | Root component with routing and global WS event dispatcher |
| `pages/DashboardPage.jsx` | Live metrics + real-time event sidebar |
| `pages/JobMatchesPage.jsx` | Paginated job list with LLM score breakdowns and advisor tips |
| `pages/ResumePage.jsx` | Resume upload and parsed profile viewer |
| `pages/TrackingPage.jsx` | Application status board — rows highlight live on WS events |
| `pages/CompaniesPage.jsx` | Add/remove companies for Adzuna to scrape |
| `pages/ClusterPage.jsx` | Live cluster topology with singleton location and event log |
| `hooks/useWebSocket.js` | Auto-reconnecting WebSocket hook used by App.jsx |
| `services/index.js` | All REST API calls — jobs, resume, tracking, cluster, companies |
| `vite.config.js` | Dev server proxy — `NODE_PORT` env var picks which backend node |

---

## 4. Prerequisites

| Tool | Version | Install (Windows) |
|------|---------|---------|
| Java JDK | 21+ | `winget install EclipseAdoptium.Temurin.21.JDK` |
| Maven | 3.9+ | `winget install Apache.Maven` |
| Node.js | 18+ | `winget install OpenJS.NodeJS` |
| PostgreSQL | 14+ | `winget install PostgreSQL.PostgreSQL` |

**External API accounts needed:**

| Service | Free tier | Sign up |
|---------|-----------|---------|
| Groq LLM | 500k tokens/day | [console.groq.com](https://console.groq.com) |
| Adzuna Jobs API | 250 calls/day | [developer.adzuna.com](https://developer.adzuna.com) |
| Gmail App Password | Free | [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords) (needs 2FA) |
| Slack Webhook | Free | Optional — for job match alerts |

---

## 5. Database Setup

Run these commands once in PowerShell:

```powershell
# 1. Create database and user
psql -U postgres -c "CREATE DATABASE jobgraph;"
psql -U postgres -c "CREATE USER jobgraph WITH PASSWORD 'jobgraph';"
psql -U postgres -c "GRANT ALL PRIVILEGES ON DATABASE jobgraph TO jobgraph;"
psql -U postgres -d jobgraph -c "GRANT ALL ON SCHEMA public TO jobgraph;"

# 2. Run schema, migrations, and seed data
psql -U jobgraph -d jobgraph -f backend/create_schema.sql
psql -U jobgraph -d jobgraph -f backend/migration_001_users_and_alerts.sql
psql -U jobgraph -d jobgraph -f backend/pre-flight.sql

# 3. Grant permissions on all tables (run after every schema change)
psql -U postgres -d jobgraph -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO jobgraph;"
psql -U postgres -d jobgraph -c "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO jobgraph;"
```

---

## 6. Configuration

Open `backend/src/main/resources/application.yml`.

> **CRITICAL RULE:** The `email:`, `demo:`, `llm:`, and all other app settings must be **indented under `jobgraph:`** — not at the root level of the file. If `email:` sits at root level, Spring cannot bind it and `enabled` stays `false` silently.

```yaml
server:
  port: ${SERVER_PORT:8080}

spring:
  application:
    name: jobgraph-backend
  datasource:
    url: jdbc:postgresql://localhost:5432/jobgraph
    username: jobgraph
    password: jobgraph
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
    open-in-view: false
  jackson:
    serialization:
      write-dates-as-timestamps: false

jobgraph:
  akka:
    config-file: ${AKKA_CONFIG:akka-common.conf}

  polling:
    interval-minutes: 1            # 1 min for demo; change to 15 in production

  matching:
    alert-threshold: 50            # Slack alert when score >= this value

  demo:
    mode: true                     # Tracker skips company matching for demo

  adzuna:
    app-id: c69785da
    app-key: c0a04e50b72c433a48ceeeb1e74952e1

  llm:
    provider: groq
    api-key: YOUR_GROQ_API_KEY     # ← paste your key here
    model: llama-3.3-70b-versatile
    base-url: https://api.groq.com/openai/v1

  slack:
    webhook-url: YOUR_SLACK_WEBHOOK_URL   # optional
    channel: "#jobgraph-alerts"

  email:
    enabled: true                  # ← must be true for tracker to work
    imap-host: imap.gmail.com
    imap-port: 993
    username: YOUR_GMAIL_ADDRESS   # ← your full Gmail address
    password: "yourapppassword"    # ← 16-char app password, NO spaces
    folder: INBOX
    fetch-batch-size: 1            # 1 email per poll for clean demo
    poll-interval-minutes: 1

logging:
  level:
    root: INFO
    com.jobgraph: DEBUG
    akka: INFO
```

---

## 7. Running the Application

### Single node (simplest — for basic testing)

```powershell
cd jobgraph-backend\backend

mvn clean spring-boot:run "-Dspring-boot.run.jvmArguments=-Djobgraph.akka.config-file=application-node1.conf -Dserver.port=8081 -Dspring.jpa.hibernate.ddl-auto=none -Djobgraph.llm.model=llama-3.3-70b-versatile -Djobgraph.llm.api-key=YOUR_GROQ_KEY"
```

Wait for: `Started JobGraphApplication in X seconds`

### Three nodes (required for cluster singleton demo)

Open 3 separate PowerShell terminals. **Start node1 first. Wait 5 seconds. Then start node2 and node3.**

**Terminal 1 — Node 1 (scheduler singleton starts here):**
```powershell
cd jobgraph-backend\backend
mvn clean spring-boot:run "-Dspring-boot.run.jvmArguments=-Djobgraph.akka.config-file=application-node1.conf -Dserver.port=8081 -Dspring.jpa.hibernate.ddl-auto=none -Djobgraph.llm.model=llama-3.3-70b-versatile -Djobgraph.llm.api-key=YOUR_GROQ_KEY" 2>&1 | Tee-Object -FilePath node1.log
```

**Terminal 2 — Node 2:**
```powershell
cd jobgraph-backend\backend
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Djobgraph.akka.config-file=application-node2.conf -Dserver.port=8082 -Dspring.jpa.hibernate.ddl-auto=none -Djobgraph.llm.model=llama-3.3-70b-versatile -Djobgraph.llm.api-key=YOUR_GROQ_KEY" 2>&1 | Tee-Object -FilePath node2.log
```

**Terminal 3 — Node 3:**
```powershell
cd jobgraph-backend\backend
mvn spring-boot:run "-Dspring-boot.run.jvmArguments=-Djobgraph.akka.config-file=application-node3.conf -Dserver.port=8083 -Dspring.jpa.hibernate.ddl-auto=none -Djobgraph.llm.model=llama-3.3-70b-versatile -Djobgraph.llm.api-key=YOUR_GROQ_KEY" 2>&1 | Tee-Object -FilePath node3.log
```

**Confirm all 3 nodes are up:**
```powershell
curl http://localhost:8081/api/cluster/status
```

---

## 8. Running the Frontend

```powershell
cd jobgraph-backend\frontend
$env:NODE_PORT=8081; npm run dev
```

Open `http://localhost:5173` in your browser.

To switch to a different node (e.g., after killing node1):
```powershell
# Stop Vite with Ctrl+C, then:
$env:NODE_PORT=8082; npm run dev
```

---

## 9. Demo: Cluster Singleton Failover

### What is the singleton?

`SchedulerAgent` is an **Akka Cluster Singleton**. Akka guarantees exactly one instance runs anywhere in the cluster at all times. It owns the periodic tick that drives all 8 agents. If the node it lives on is killed, Akka's Split Brain Resolver detects the unreachable node and automatically starts the singleton on the next eligible node — no manual action required.

### Step-by-step

**Step 1** — Start all 3 nodes (Terminal 1, 2, 3 as shown above)

**Step 2** — Open the frontend → navigate to **Cluster** page

You will see:
- 3 green nodes all showing `Up`
- `★ singleton` label next to node1 (127.0.0.1:2551)
- Leader address: `127.0.0.1:2551`

**Step 3** — Verify the app is working  
Go to Dashboard — jobs being polled, scores being calculated.

**Step 4** — Kill node1  
Press `Ctrl+C` in Terminal 1.

**Step 5** — Watch the Cluster page — do not touch anything  
Within ~25 seconds the topology event log shows:
```
Node left: 127.0.0.1:2551
```
The `★ singleton` label moves to node2 (127.0.0.1:2552).

**Step 6** — Switch frontend to node2
```powershell
$env:NODE_PORT=8082; npm run dev
```

**Step 7** — Show the app still works  
Dashboard, Job Matches, Tracking — all functional. Scheduler kept ticking on node2.

### What to say to your professor

*"This is Akka's Cluster Singleton pattern. SchedulerAgent is guaranteed to run on exactly one node at all times. When node1 was killed, Akka's Split Brain Resolver detected the unreachable node and the singleton manager on node2 automatically took over within 25 seconds — no manual intervention, no data loss, no restart required. This is what makes the system fault-tolerant."*

### What appears in node2.log after node1 dies:

```
ClusterSingletonManager state change [Younger -> Oldest]
SchedulerAgent started (singleton) — ticking every 1 min
Scheduler tick — 1 users
Scheduler fanned out 5 PollAdzuna + 1 PollEmails messages
```

---

## 10. Demo: Resume Upload → Immediate Polling

### The problem without this feature

Normally the scheduler polls every 15 minutes. A brand-new user who just uploaded their resume would wait up to 15 minutes before seeing any job matches.

### What was changed

`ResumeAnalyzerAgent` now fans out one `PollAdzuna` message per preferred role immediately after the LLM finishes parsing the resume — bypassing the scheduler wait entirely.

### Demo steps

**Step 1** — Navigate to **Resume** in the sidebar

**Step 2** — Upload `backend/resume.txt` (or paste resume text)

**Step 3** — Immediately switch to **Dashboard**

Within 5–10 seconds (before any scheduler tick):
```
NEW_JOB      Acme Corp · Software Engineer
MATCH_SCORE  score 74 · job #5
MATCH_SCORE  score 68 · job #6
```

### What to say

*"As soon as the LLM extracts preferred roles from the resume, ResumeAnalyzerAgent directly messages PollerAgent — one message per role. The user sees job matches within seconds of uploading their resume, not after the next 15-minute scheduler cycle. This is a direct actor-to-actor message bypassing the scheduler singleton entirely."*

---

## 11. Demo: Email Tracker Agent

### What it does

TrackerAgent connects to Gmail every minute via IMAP, fetches the latest unread email, sends it to the Groq LLM for classification, and if the result is INTERVIEW_INVITE, REJECTION, or OFFER with confidence ≥ 70%, it automatically updates the application tracking status and pushes a WebSocket event to the frontend.

### Pre-demo setup

```powershell
# Clear seen emails so tracker processes fresh
psql -U postgres -d jobgraph -c "DELETE FROM seen_emails;"
```

Confirm `application.yml` has these settings under `jobgraph:`:
```yaml
  demo:
    mode: true          # skips company name matching
  email:
    enabled: true
    fetch-batch-size: 1
    poll-interval-minutes: 1
```

### Demo steps

**Step 1** — Track a job  
Go to Job Matches → click **Track** on any job. This creates an application tracking row with status `BOOKMARKED`.

**Step 2** — Open the **Tracking** page and leave it visible

**Step 3** — Send a test email to your Gmail  
From any other email account, send to `hardishah1101@gmail.com`:
- Subject: `Interview invitation — Software Engineer role`
- Body: `We would like to invite you for a phone screen interview next week. Please let us know your availability.`
- **Keep it unread in Gmail — do NOT open it**

**Step 4** — Wait ~1 minute for the scheduler tick

Watch the logs:
```
IMAP fetched 378 unread messages (processing 1)
Email classified: INTERVIEW_INVITE (conf=0.91) company='...' — invitation to phone screen
Tracking 1 auto-updated: BOOKMARKED -> PHONE_SCREEN
```

**Step 5** — Watch the Tracking page  
The row glows indigo and shows `Phone Screen` — no page refresh needed.

### What to say

*"TrackerAgent uses Jakarta Mail to connect to Gmail via IMAPS. When it finds an unread email, it sends the subject, sender, and body snippet to the LLM which classifies it as one of six labels — REJECTION, INTERVIEW_INVITE, OFFER, FOLLOW_UP, GENERIC, or UNRELATED — with a confidence score. If confidence is above 70% and the label is actionable, it automatically updates the application status and fires a WebSocket event. The user sees their tracking board update in real time without doing anything."*

### Status transition logic

| Email Classification | Status Transition |
|---------------------|-------------------|
| `INTERVIEW_INVITE` | BOOKMARKED/APPLIED → PHONE_SCREEN, PHONE_SCREEN → INTERVIEW |
| `REJECTION` | Any → REJECTED |
| `OFFER` | Any → OFFER |
| `FOLLOW_UP`, `GENERIC`, `UNRELATED` | No status change (still saved as audit record) |

### Demo mode vs production mode

| | `demo.mode: true` | `demo.mode: false` |
|--|--|--|
| Matching | Updates most recent open tracking row | Requires email sender to match tracked company name |
| Use | Demo / testing | Production |

---

## 12. API Reference

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/api/resumes?userId=1` | Upload resume text for LLM parsing |
| `GET` | `/api/resumes/latest?userId=1` | Get latest parsed resume profile |
| `GET` | `/api/jobs/matches?userId=1&page=0&size=20` | Get scored job matches paginated |
| `GET` | `/api/jobs/dashboard?userId=1` | Dashboard stats and metrics |
| `GET` | `/api/companies` | List all tracked companies |
| `POST` | `/api/companies` | Add a company for Adzuna to scrape |
| `PATCH` | `/api/companies/{id}/toggle` | Enable or disable a company |
| `DELETE` | `/api/companies/{id}` | Remove a company |
| `GET` | `/api/tracking?userId=1` | List all application tracking rows |
| `POST` | `/api/tracking?jobId=1&resumeId=1` | Start tracking a job application |
| `PATCH` | `/api/tracking/{id}` | Manually update application status |
| `GET` | `/api/cluster/status` | Live cluster topology snapshot |
| `WS` | `/ws/jobs` | WebSocket endpoint for real-time events |

---

## 13. Troubleshooting

### App won't start — `clusterManager` bean creation error
```
The method create(...) is not applicable for the arguments (...)
```
You replaced one Java file but not all of them. Always run `mvn clean spring-boot:run` — the `clean` wipes the `target/` directory and forces a full recompile.

---

### Tracking insert fails — `column "status" is of type application_status`
The `ApplicationTracking.java` entity needs `columnDefinition` on the status field:
```java
@Enumerated(EnumType.STRING)
@Column(nullable = false, columnDefinition = "application_status")
@Builder.Default
private ApplicationStatus status = ApplicationStatus.BOOKMARKED;
```
After fixing, run `mvn clean spring-boot:run`.

---

### Email shows "no new mail" every tick — IMAP never connects
Check in order:
1. `email:` block is **under `jobgraph:`** in application.yml — not at root level
2. `enabled: true` is set
3. No log line saying `"IMAP fetched X unread messages"` means it never connected
4. Run `DELETE FROM seen_emails;` — emails may already be marked as processed

---

### `IMAP authentication failed`
The Gmail app password is wrong or expired.
1. Go to [myaccount.google.com/apppasswords](https://myaccount.google.com/apppasswords)
2. Delete the old app password, create a new one named "JobGraph"
3. Copy the 16-character code — **no spaces** — paste as `password: "abcdabcdabcdabcd"`

---

### Groq 429 — retry after 1852000 ms
You hit the free tier rate limit (~30 req/min). The code retries automatically. For demo purposes avoid triggering resume upload and job polling simultaneously. Use `llama-3.3-70b-versatile` — it has a separate quota from `llama3-70b-8192`.

---

### `permission denied for table seen_emails`
```sql
psql -U postgres -d jobgraph -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO jobgraph;"
psql -U postgres -d jobgraph -c "GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO jobgraph;"
```

---

### Frontend shows "cannot reach backend"
The Vite proxy is pointing to the wrong port. Stop Vite and restart with the correct node:
```powershell
$env:NODE_PORT=8081; npm run dev    # node1 is running
$env:NODE_PORT=8082; npm run dev    # node1 is dead, use node2
$env:NODE_PORT=8083; npm run dev    # only node3 is running
```

---

### Cluster page shows only 1 node
Node2 and node3 are not started or haven't joined yet. Confirm:
- Node1 was started first and fully booted before node2
- Ports 2551, 2552, 2553 are not blocked by Windows Firewall
- All three nodes use the same `akka-common.conf` seed-nodes list

---

### `TrackerAgent started (demoMode=false)` — should be true
You haven't deployed the new `TrackerAgent.java`. Copy the file from the outputs folder to `backend/src/main/java/com/jobgraph/agent/TrackerAgent.java` and run `mvn clean spring-boot:run`.

---

## 14. Quick Demo Checklist

Before starting your demo, confirm all of these:

```
□ PostgreSQL is running
□ Schema + migrations + seed data have been applied
□ Table permissions granted: GRANT ALL PRIVILEGES ON ALL TABLES...
□ application.yml: email: block is indented UNDER jobgraph:
□ application.yml: email.enabled = true
□ application.yml: demo.mode = true
□ application.yml: polling.interval-minutes = 1
□ Gmail app password has NO spaces (16 chars only)
□ seen_emails table cleared: DELETE FROM seen_emails;
□ Node1 started first, then node2, then node3
□ All 3 nodes showing Up in /api/cluster/status
□ Frontend running: $env:NODE_PORT=8081; npm run dev
□ Log shows: TrackerAgent started (demoMode=true)
□ Log shows: IMAP fetched X unread messages (after first tick)
□ At least one job tracked on the Tracking page
□ Test email sent to Gmail and kept UNREAD
```