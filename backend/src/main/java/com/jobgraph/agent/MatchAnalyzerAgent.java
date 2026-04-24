package com.jobgraph.agent;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobgraph.message.AdvisorMessages.AdviseForJob;
import com.jobgraph.message.MatchMessages.*;
import com.jobgraph.message.MatchMessages.ScoreJob;
import com.jobgraph.message.MatchMessages.ScoreResult;
import com.jobgraph.message.NotificationMessages.SendSlack;
import com.jobgraph.model.Job;
import com.jobgraph.model.JobAlert;
import com.jobgraph.model.MatchScore;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.repository.JobAlertRepository;
import com.jobgraph.repository.JobRepository;
import com.jobgraph.repository.MatchScoreRepository;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.service.LlmService;
import com.jobgraph.websocket.JobUpdateHandler;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class MatchAnalyzerAgent extends AbstractBehavior<ScoreJob> {

    private final LlmService llmService;
    private final JobRepository jobRepository;
    private final ResumeProfileRepository resumeRepository;
    private final MatchScoreRepository matchRepository;
    private final JobAlertRepository alertRepository;
    private final ActorRef<SendSlack> notificationRef;
    private final ActorRef<AdviseForJob> advisorRef;
    private final JobUpdateHandler jobUpdateHandler;
    private final double alertThreshold;

    /**
     * Single system prompt. Structured in sections so we can iterate on one
     * category without rewriting the whole thing. The experience section is
     * the most carefully specified because that's what the user asked for.
     */
    private static final String SYSTEM_PROMPT = """
        ROLE
        You are a rigorous job-match scoring engine. You score how well a
        candidate fits a job across five categories. Your output feeds an
        alerting system, so calibration matters: do not inflate scores.

        OUTPUT
        Return ONLY a JSON object — no prose, no markdown fences:
        {
          "overallScore": number (0-100),
          "skillScore": number, "skillDetail": string,
          "experienceScore": number, "experienceDetail": string,
          "educationScore": number, "educationDetail": string,
          "industryScore": number, "industryDetail": string,
          "locationScore": number, "locationDetail": string
        }
        overallScore is the WEIGHTED average: 40% skills, 30% experience,
        10% education, 10% industry, 10% location. Compute it yourself.

        SCORING RUBRIC

        skillScore (0-100):
          - Count required skills from the job description.
          - Compute the % the candidate demonstrably has (in skills list OR
            evidenced in project/job bullets).
          - 90+ = has nearly all required + most nice-to-haves.
          - 70  = has most required, missing a couple.
          - 50  = has ~half. 30 = has a few. 0 = almost none.

        experienceScore (0-100):  ← HIGHEST-PRIORITY RUBRIC
          Evaluate three dimensions, then combine:

          (a) Years gap.  Let req = years required by the JD (infer from
              phrases like "5+ years", "senior", "junior", "entry-level",
              or default to the midpoint if a range is given). Let have =
              candidate's experienceYears.
                 have >= req          → 100
                 have = req - 1       → 80
                 have = req - 2       → 60
                 have = req - 3       → 35
                 have <= req - 4      → 15
                 Reverse gap (over-qualified by 5+ years) → 70
                   (not 100, because over-qualified candidates often pass)

          (b) Domain / type match. Is the candidate's experience in the
              SAME KIND of work the job requires? Examples:
                SAME domain (e.g. both "backend distributed systems")   → ×1.0
                ADJACENT domain (e.g. "backend APIs" vs "data pipelines") → ×0.85
                TRANSFERABLE but different (e.g. "mobile apps" for a backend role) → ×0.6
                UNRELATED (e.g. frontend-only candidate for an SRE role) → ×0.3

          (c) Progression / seniority signal. Look for leadership, scope,
              ownership in the resume bullets (led / owned / designed /
              scaled / mentored). Senior/Staff jobs require this.
                Strong signal  → +10
                Some signal    → +0
                No signal      → -15 (penalty for senior-titled JDs only)

          Final: (a) * (b) + (c), clamped to [0, 100].

          In `experienceDetail`, state the inferred req (years), the
          candidate's have, the domain match ruling, and the final number.
          Example: "Req ~5y, have 2y (gap=3 → 35), adjacent domain (×0.85),
          no leadership signal for senior role (-15). Final: 15."

        educationScore (0-100):
          Required level met or exceeded → 100.
          One level below → 70. Two below → 40. None specified → 90.

        industryScore (0-100):
          Direct industry overlap → 100. Adjacent (e.g. fintech → payments)
          → 75. Unrelated → 40. Unspecified → 80.

        locationScore (0-100):
          "Remote" in the job location or description → 100.
          Same city/metro as candidate → 95. Same country → 70.
          Requires relocation, no remote → 40.

        IMPORTANT
          - Be specific in each *Detail field (1-2 sentences, cite evidence).
          - Never return placeholder text like "N/A" — pick a score.
          - Do not round to multiples of 10 unless genuinely warranted.
        """;

    private MatchAnalyzerAgent(ActorContext<ScoreJob> ctx,
                               LlmService llmService,
                               JobRepository jobRepo,
                               ResumeProfileRepository resumeRepo,
                               MatchScoreRepository matchRepo,
                               JobAlertRepository alertRepo,
                               ActorRef<SendSlack> notificationRef,
                               ActorRef<AdviseForJob> advisorRef,
                               JobUpdateHandler jobUpdateHandler,
                               double alertThreshold) {
        super(ctx);
        this.llmService = llmService;
        this.jobRepository = jobRepo;
        this.resumeRepository = resumeRepo;
        this.matchRepository = matchRepo;
        this.alertRepository = alertRepo;
        this.notificationRef = notificationRef;
        this.advisorRef = advisorRef;
        this.jobUpdateHandler = jobUpdateHandler;
        this.alertThreshold = alertThreshold;
        log.info("MatchAnalyzerAgent started — alert threshold: {}", alertThreshold);
    }

    public static Behavior<ScoreJob> create(LlmService llm,
                                             JobRepository jobRepo,
                                             ResumeProfileRepository resumeRepo,
                                             MatchScoreRepository matchRepo,
                                             JobAlertRepository alertRepo,
                                             ActorRef<SendSlack> notificationRef,
                                             ActorRef<AdviseForJob> advisorRef,
                                             JobUpdateHandler jobUpdateHandler,
                                             double alertThreshold) {
        return Behaviors.setup(ctx ->
                new MatchAnalyzerAgent(ctx, llm, jobRepo, resumeRepo,
                        matchRepo, alertRepo, notificationRef, advisorRef,
                        jobUpdateHandler, alertThreshold));
    }

    @Override
    public Receive<ScoreJob> createReceive() {
        return newReceiveBuilder()
                .onMessage(ScoreJob.class, this::onScore)
                .build();
    }

    private Behavior<ScoreJob> onScore(ScoreJob cmd) {
        try {
            Job job = jobRepository.findById(cmd.getJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found: " + cmd.getJobId()));
            ResumeProfile resume = resumeRepository.findById(cmd.getResumeId())
                    .orElseThrow(() -> new RuntimeException("Resume not found: " + cmd.getResumeId()));

            MatchScore fresh = computeScore(job, resume);

            // Upsert: if a score already exists for this (job, resume) pair,
            // overwrite its fields in place; otherwise save the new one.
            // This lets the pipeline re-poll the same jobs without blowing
            // up on the (job_id, resume_id) unique constraint.
            MatchScore score = matchRepository
                    .findByJobIdAndResumeId(cmd.getJobId(), cmd.getResumeId())
                    .map(existing -> applyUpdate(existing, fresh))
                    .orElse(fresh);
            score = matchRepository.save(score);

            log.info("Match scored: job={} resume={} overall={}",
                    cmd.getJobId(), cmd.getResumeId(), score.getOverallScore());

            // Broadcast to any connected WS clients so the UI can live-update
            // score pills without polling. Fires on every score — fresh or
            // re-computed — since the score value itself may have changed.
            jobUpdateHandler.broadcastMatchScore(
                    cmd.getJobId(), cmd.getResumeId(), score.getOverallScore());

            // ResumeAdvisor: trigger only for the improvable band.
            //   < 60  → not worth the LLM call, probably a bad fit anyway
            //   ≥ 90  → already great, no advice needed
            //   60-90 → room to grow, advice genuinely helps the user
            double s = score.getOverallScore();
            if (s >= 60 && s < 90) {
                advisorRef.tell(new AdviseForJob(
                        score.getId(),
                        cmd.getJobId(),
                        cmd.getResumeId(),
                        getContext().getSystem().ignoreRef()));
            }

            // Fire alert consolidation: collects ALL resumes for this user that
            // scored over threshold on this job, and sends one Slack message.
            maybeAlert(job, resume);

            cmd.getReplyTo().tell(new ScoreResult(
                    cmd.getJobId(), cmd.getResumeId(),
                    score.getOverallScore(),
                    score.getSkillScore(), score.getExperienceScore(),
                    score.getEducationScore(), score.getIndustryScore(),
                    score.getLocationScore(),
                    score.getSkillDetail(), score.getExperienceDetail(),
                    score.getEducationDetail(), score.getIndustryDetail(),
                    score.getLocationDetail(),
                    true, null));

        } catch (Exception e) {
            log.error("Match scoring failed for job={} resume={}",
                    cmd.getJobId(), cmd.getResumeId(), e);
            cmd.getReplyTo().tell(new ScoreResult(
                    cmd.getJobId(), cmd.getResumeId(),
                    0, 0, 0, 0, 0, 0,
                    null, null, null, null, null,
                    false, e.getMessage()));
        }
        return this;
    }

    // ──────────────────────────────────────────────────────────
    //  Scoring
    // ──────────────────────────────────────────────────────────

    private MatchScore computeScore(Job job, ResumeProfile resume) {
        // Give the LLM structured context about the candidate rather than
        // dumping rawText blindly — this improves experience reasoning a lot.
        String resumeContext = String.format("""
                Candidate profile:
                  fullName: %s
                  experienceYears: %d
                  educationLevel: %s
                  skills: %s
                  preferredRoles: %s

                Full resume text:
                %s
                """,
                resume.getFullName(),
                resume.getExperienceYears() != null ? resume.getExperienceYears() : 0,
                resume.getEducationLevel(),
                resume.getSkills(),
                resume.getPreferredRoles(),
                resume.getRawText());

        String userPrompt = String.format("""
                %s

                JOB:
                  title: %s
                  location: %s
                  description: %s
                """, resumeContext, job.getTitle(),
                job.getLocation(), job.getDescription());

        JsonNode result = llmService.chatJson(SYSTEM_PROMPT, userPrompt);

        return MatchScore.builder()
                .job(job)
                .resume(resume)
                .overallScore(clamp(result.path("overallScore").asDouble()))
                .skillScore(clamp(result.path("skillScore").asDouble()))
                .experienceScore(clamp(result.path("experienceScore").asDouble()))
                .educationScore(clamp(result.path("educationScore").asDouble()))
                .industryScore(clamp(result.path("industryScore").asDouble()))
                .locationScore(clamp(result.path("locationScore").asDouble()))
                .skillDetail(result.path("skillDetail").asText())
                .experienceDetail(result.path("experienceDetail").asText())
                .educationDetail(result.path("educationDetail").asText())
                .industryDetail(result.path("industryDetail").asText())
                .locationDetail(result.path("locationDetail").asText())
                .computedAt(Instant.now())
                .build();
    }

    private static double clamp(double v) {
        if (Double.isNaN(v)) return 0;
        return Math.max(0, Math.min(100, v));
    }

    /**
     * Copy the freshly-computed scores and details into an existing MatchScore
     * row so JPA issues an UPDATE instead of an INSERT. We keep the original
     * row's id/job/resume references untouched.
     */
    private static MatchScore applyUpdate(MatchScore existing, MatchScore fresh) {
        existing.setOverallScore(fresh.getOverallScore());
        existing.setSkillScore(fresh.getSkillScore());
        existing.setExperienceScore(fresh.getExperienceScore());
        existing.setEducationScore(fresh.getEducationScore());
        existing.setIndustryScore(fresh.getIndustryScore());
        existing.setLocationScore(fresh.getLocationScore());
        existing.setSkillDetail(fresh.getSkillDetail());
        existing.setExperienceDetail(fresh.getExperienceDetail());
        existing.setEducationDetail(fresh.getEducationDetail());
        existing.setIndustryDetail(fresh.getIndustryDetail());
        existing.setLocationDetail(fresh.getLocationDetail());
        existing.setComputedAt(fresh.getComputedAt());
        return existing;
    }

    // ──────────────────────────────────────────────────────────
    //  Alerting
    // ──────────────────────────────────────────────────────────

    /**
     * If this (user, job) already has an alert, do nothing.
     * Otherwise: collect ALL match_scores for this user+job, filter to
     * those above threshold, and send one consolidated Slack message.
     */
    private void maybeAlert(Job job, ResumeProfile resume) {
        Long userId = resume.getUser().getId();

        // Dedup: one alert per (user, job), regardless of how many resumes
        // eventually cross the threshold.
        if (alertRepository.existsByUserIdAndJobId(userId, job.getId())) {
            return;
        }

        // Gather every resume this user has scored against this job.
        List<MatchScore> allScores = matchRepository
                .findByJobIdAndUserId(job.getId(), userId);

        // Only resumes that cross the threshold qualify for the alert.
        List<MatchScore> qualifying = allScores.stream()
                .filter(s -> s.getOverallScore() >= alertThreshold)
                .sorted((a, b) -> Double.compare(b.getOverallScore(), a.getOverallScore()))
                .toList();

        if (qualifying.isEmpty()) return;

        double topScore = qualifying.get(0).getOverallScore();

        // Resolve every lazy field we will need for Slack WHILE the Hibernate
        // session is still open. After this point we only work with plain
        // strings/primitives, so no LazyInitializationException can occur.
        String companyName = safeCompanyName(job);
        List<ResumeLine> resumeLines = qualifying.stream()
                .map(s -> new ResumeLine(
                        safeResumeLabel(s),
                        s.getOverallScore(),
                        s.getSkillDetail()))
                .toList();

        String message = buildSlackMessage(job, companyName, resumeLines);

        // Delegate delivery to NotificationAgent. We use ignoreRef() because
        // we don't need to block on the Slack round-trip — the JobAlert row
        // below is our durable record that an alert was dispatched.
        notificationRef.tell(new SendSlack(
                "#jobgraph-alerts",
                message,
                getContext().getSystem().ignoreRef()));

        alertRepository.save(JobAlert.builder()
                .userId(userId)
                .jobId(job.getId())
                .topScore(topScore)
                .resumeCount(qualifying.size())
                .build());
        log.info("Alert dispatched: job={} user={} top={} resumes={}",
                job.getId(), userId, topScore, qualifying.size());
    }

    /** Defensive accessor — tolerates detached proxies. */
    private static String safeCompanyName(Job job) {
        try {
            return (job.getCompany() != null) ? job.getCompany().getName() : null;
        } catch (org.hibernate.LazyInitializationException e) {
            return null;
        }
    }

    /** Defensive accessor — tolerates detached proxies. */
    private static String safeResumeLabel(MatchScore s) {
        try {
            return (s.getResume() != null) ? s.getResume().getDisplayLabel() : "(resume)";
        } catch (org.hibernate.LazyInitializationException e) {
            return "(resume)";
        }
    }

    /** Eagerly-resolved view of a qualifying resume row — safe to use outside the session. */
    private record ResumeLine(String label, double overallScore, String skillDetail) {}

    private String buildSlackMessage(Job job, String companyName, List<ResumeLine> qualifying) {
        StringBuilder sb = new StringBuilder();
        sb.append(":briefcase: *New matching job*: ")
          .append(job.getTitle());
        if (companyName != null && !companyName.isBlank()) {
            sb.append(" at ").append(companyName);
        }
        if (job.getLocation() != null) {
            sb.append(" (").append(job.getLocation()).append(")");
        }

        // Direct apply link — Adzuna redirect_url or any adapter's canonical URL.
        if (job.getApplyUrl() != null && !job.getApplyUrl().isBlank()) {
            sb.append("\n<").append(job.getApplyUrl()).append("|Open job posting →>");
        }

        sb.append("\n\n*Matching resumes:*\n");
        for (ResumeLine s : qualifying) {
            sb.append("• ")
              .append(s.label())
              .append(" — *")
              .append(String.format("%.0f", s.overallScore()))
              .append("*");
            if (s.skillDetail() != null && !s.skillDetail().isBlank()) {
                sb.append("  _").append(s.skillDetail()).append("_");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}