package com.jobgraph.agent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobgraph.message.AdvisorMessages.*;
import com.jobgraph.message.AdvisorMessages.AdviceResult;
import com.jobgraph.message.AdvisorMessages.AdviseForJob;
import com.jobgraph.model.Job;
import com.jobgraph.model.MatchScore;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.repository.JobRepository;
import com.jobgraph.repository.MatchScoreRepository;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.service.LlmService;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.extern.slf4j.Slf4j;

/**
 * Generates tailored resume improvement advice for a specific job match.
 *
 * Fired by MatchAnalyzerAgent only when overall score falls in the
 * "improvable band" (60–90). Below 60 means the job likely isn't a fit —
 * advice wouldn't change the outcome. Above 90 means the resume is already
 * strong — no advice is needed.
 *
 * Output is persisted as structured JSON on match_scores.resume_tips, so
 * the frontend can render tips, missing skills, and the rewritten summary
 * as separate sections of the "Why this match" page.
 */
@Slf4j
public class ResumeAdvisorAgent extends AbstractBehavior<AdviseForJob> {

    private final LlmService llmService;
    private final MatchScoreRepository matchRepo;
    private final JobRepository jobRepo;
    private final ResumeProfileRepository resumeRepo;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        ROLE
        You are an experienced technical recruiter and resume coach. You
        just reviewed a candidate's resume against a job they'd *almost*
        qualify for — the match score is between 60 and 90. Your job is
        to give actionable, specific advice on how they can close the gap.

        GUIDELINES
        - Be specific. "Add cloud experience" is useless. "Add a bullet
          about your AWS Lambda work at Acme Corp, emphasizing cost
          savings" is useful.
        - Quote real items from the resume when rewriting. Don't invent
          accomplishments the candidate didn't do.
        - If the resume has relevant experience buried in a less
          prominent role, say so. Example: "Move your Kafka work from
          the Intern section up to the Summary — it directly matches
          their streaming-data requirement."
        - missingSkills should be TECHNICAL or DOMAIN skills the JD
          requires and the resume doesn't demonstrate. Don't list
          soft skills like 'communication'.

        OUTPUT
        Respond ONLY with JSON, no markdown, no prose:
        {
          "tips": [
            "Specific, actionable sentence (15-30 words).",
            "Another specific sentence."
          ],
          "missingSkills": ["Skill 1", "Skill 2"],
          "rewrittenSummary": "A 2-3 sentence professional summary that
                               would make this candidate a stronger fit
                               for this specific job, using only their
                               real experience."
        }

        Provide 3-5 tips. Keep each tip independent — no numbering, no
        'Additionally' connectors; the frontend displays them as bullets.
        """;

    private ResumeAdvisorAgent(ActorContext<AdviseForJob> ctx,
                               LlmService llmService,
                               MatchScoreRepository matchRepo,
                               JobRepository jobRepo,
                               ResumeProfileRepository resumeRepo) {
        super(ctx);
        this.llmService = llmService;
        this.matchRepo = matchRepo;
        this.jobRepo = jobRepo;
        this.resumeRepo = resumeRepo;
        log.info("ResumeAdvisorAgent started");
    }

    public static Behavior<AdviseForJob> create(LlmService llm,
                                                 MatchScoreRepository matchRepo,
                                                 JobRepository jobRepo,
                                                 ResumeProfileRepository resumeRepo) {
        return Behaviors.setup(ctx -> new ResumeAdvisorAgent(ctx, llm, matchRepo, jobRepo, resumeRepo));
    }

    @Override
    public Receive<AdviseForJob> createReceive() {
        return newReceiveBuilder()
                .onMessage(AdviseForJob.class, this::onAdvise)
                .build();
    }

    private Behavior<AdviseForJob> onAdvise(AdviseForJob cmd) {
        try {
            Job job = jobRepo.findById(cmd.getJobId())
                    .orElseThrow(() -> new RuntimeException("Job not found: " + cmd.getJobId()));
            ResumeProfile resume = resumeRepo.findById(cmd.getResumeId())
                    .orElseThrow(() -> new RuntimeException("Resume not found: " + cmd.getResumeId()));
            MatchScore match = matchRepo.findById(cmd.getMatchScoreId())
                    .orElseThrow(() -> new RuntimeException("Match not found: " + cmd.getMatchScoreId()));

            // Give the LLM the match's sub-scores so its advice targets
            // the weakest category first. No point rewriting the summary
            // if the real gap is missing AWS experience.
            String userPrompt = String.format(
                    "CANDIDATE RESUME:\n%s\n\n" +
                    "JOB TITLE: %s\n" +
                    "JOB DESCRIPTION:\n%s\n\n" +
                    "CURRENT MATCH BREAKDOWN (out of 100):\n" +
                    "- Overall: %.0f\n" +
                    "- Skills: %.0f — %s\n" +
                    "- Experience: %.0f — %s\n" +
                    "- Education: %.0f — %s\n" +
                    "- Industry: %.0f — %s\n\n" +
                    "Focus your advice on the LOWEST sub-scores. The user " +
                    "has already earned a decent overall score — they need " +
                    "to know what to tweak to push past 90.",
                    resume.getRawText(),
                    job.getTitle(),
                    job.getDescription() == null ? "" : job.getDescription(),
                    nz(match.getOverallScore()),
                    nz(match.getSkillScore()),       nb(match.getSkillDetail()),
                    nz(match.getExperienceScore()),  nb(match.getExperienceDetail()),
                    nz(match.getEducationScore()),   nb(match.getEducationDetail()),
                    nz(match.getIndustryScore()),    nb(match.getIndustryDetail()));

            JsonNode result = llmService.chatJson(SYSTEM_PROMPT, userPrompt);

            List<String> tips = new ArrayList<>();
            result.path("tips").forEach(t -> {
                String s = t.asText();
                if (!s.isBlank()) tips.add(s);
            });

            List<String> missing = new ArrayList<>();
            result.path("missingSkills").forEach(s -> {
                String v = s.asText();
                if (!v.isBlank()) missing.add(v);
            });

            String rewritten = result.path("rewrittenSummary").asText("");

            // Persist as structured JSON — the frontend parses this back
            // out into tip list + skills list + summary paragraph.
            ObjectNode payload = objectMapper.createObjectNode();
            payload.putPOJO("tips", tips);
            payload.putPOJO("missingSkills", missing);
            payload.put("rewrittenSummary", rewritten);
            payload.put("generatedAt", Instant.now().toString());

            match.setResumeTips(objectMapper.writeValueAsString(payload));
            matchRepo.save(match);

            log.info("Advisor: {} tips, {} missing skills for match {} (score {})",
                    tips.size(), missing.size(),
                    cmd.getMatchScoreId(), match.getOverallScore());

            cmd.getReplyTo().tell(new AdviceResult(
                    cmd.getMatchScoreId(), tips, missing, rewritten, true, null));

        } catch (Exception e) {
            log.error("Resume advising failed for match {}", cmd.getMatchScoreId(), e);
            cmd.getReplyTo().tell(new AdviceResult(
                    cmd.getMatchScoreId(), List.of(), List.of(), "", false, e.getMessage()));
        }
        return this;
    }

    /** Null-safe score formatter (treats null as 0). */
    private static double nz(Double v) { return v == null ? 0 : v; }

    /** Null-safe string formatter (treats null as "n/a"). */
    private static String nb(String s) { return s == null || s.isBlank() ? "n/a" : s; }
}