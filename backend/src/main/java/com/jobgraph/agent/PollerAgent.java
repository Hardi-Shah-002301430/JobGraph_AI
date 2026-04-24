package com.jobgraph.agent;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobgraph.message.MatchMessages.ScoreJob;
import com.jobgraph.message.PollingMessages.PollAdzuna;
import com.jobgraph.message.PollingMessages.PollResult;
import com.jobgraph.model.Company;
import com.jobgraph.model.Job;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.repository.CompanyRepository;
import com.jobgraph.repository.JobRepository;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.service.JobBoardAdapter;
import com.jobgraph.websocket.JobUpdateHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * PollerAgent — now Adzuna-specific. Receives a PollAdzuna(userId, role)
 * message; does the full sub-pipeline:
 *
 *   1. Adapter fetches + scrapes jobs (Adzuna call + jsoup on each redirect)
 *   2. Upsert a Company row per distinct employer name
 *   3. Upsert each Job, keyed on (external_id, company_id)
 *   4. For each saved Job, fan out ScoreJob to the MatchAnalyzer pool,
 *      once per resume the user owns.
 *
 * When multiple instances of this agent run (pool of N), each one handles
 * its own PollAdzuna message independently — true parallelism across roles.
 */
@Slf4j
public class PollerAgent extends AbstractBehavior<PollAdzuna> {

    private final Map<String, JobBoardAdapter> adapters;
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final ResumeProfileRepository resumeRepository;
    private final ActorRef<ScoreJob> matchAnalyzerRef;
    private final JobUpdateHandler jobUpdateHandler;

    private PollerAgent(ActorContext<PollAdzuna> ctx,
                        List<JobBoardAdapter> adapterList,
                        JobRepository jobRepository,
                        CompanyRepository companyRepository,
                        ResumeProfileRepository resumeRepository,
                        ActorRef<ScoreJob> matchAnalyzerRef,
                        JobUpdateHandler jobUpdateHandler) {
        super(ctx);
        this.adapters = adapterList.stream()
                .collect(Collectors.toMap(JobBoardAdapter::boardType, Function.identity()));
        this.jobRepository = jobRepository;
        this.companyRepository = companyRepository;
        this.resumeRepository = resumeRepository;
        this.matchAnalyzerRef = matchAnalyzerRef;
        this.jobUpdateHandler = jobUpdateHandler;
        log.info("PollerAgent ready — adapters: {}", adapters.keySet());
    }

    public static Behavior<PollAdzuna> create(List<JobBoardAdapter> adapters,
                                               JobRepository jobs,
                                               CompanyRepository companies,
                                               ResumeProfileRepository resumes,
                                               ActorRef<ScoreJob> matchAnalyzer,
                                               JobUpdateHandler jobUpdateHandler) {
        return Behaviors.setup(ctx ->
                new PollerAgent(ctx, adapters, jobs, companies, resumes, matchAnalyzer, jobUpdateHandler));
    }

    @Override
    public Receive<PollAdzuna> createReceive() {
        return newReceiveBuilder()
                .onMessage(PollAdzuna.class, this::onPoll)
                .build();
    }

    private Behavior<PollAdzuna> onPoll(PollAdzuna cmd) {
        long userId = cmd.getUserId();
        String role = cmd.getRole();

        try {
            JobBoardAdapter adapter = adapters.get("ADZUNA");
            if (adapter == null) {
                log.error("No ADZUNA adapter registered");
                cmd.getReplyTo().tell(new PollResult(userId, role, 0, 0, false, "no adapter"));
                return this;
            }

            // Step 1: Adzuna fetch + scrape (blocking, but worth it per job)
            List<Job> fetched = adapter.fetchJobs(role, null);
            if (fetched.isEmpty()) {
                cmd.getReplyTo().tell(new PollResult(userId, role, 0, 0, true, null));
                return this;
            }

            // Step 2 + 3: resolve company, upsert Job
            List<Job> saved = fetched.stream()
                    .map(this::upsertJob)
                    .filter(j -> j != null)
                    .toList();

            // Step 4: fan out ScoreJob messages to the matcher pool,
            // one per (savedJob × user's resumes).
            List<ResumeProfile> resumes =
                    resumeRepository.findByUserIdOrderByCreatedAtDesc(userId);
            if (resumes.isEmpty()) {
                log.warn("User {} has no resumes — skipping scoring", userId);
                cmd.getReplyTo().tell(new PollResult(userId, role, saved.size(), 0, true, null));
                return this;
            }

            int scored = 0;
            for (Job job : saved) {
                for (ResumeProfile resume : resumes) {
                    matchAnalyzerRef.tell(new ScoreJob(
                            job.getId(),
                            resume.getId(),
                            getContext().getSystem().ignoreRef()));
                    scored++;
                }
            }

            log.info("Poll done user={} role='{}' — fetched {}, scored {} (jobs×resumes)",
                    userId, role, saved.size(), scored);
            cmd.getReplyTo().tell(new PollResult(userId, role, saved.size(), scored, true, null));

        } catch (Exception e) {
            log.error("Poll failed for user={} role='{}'", userId, role, e);
            cmd.getReplyTo().tell(new PollResult(userId, role, 0, 0, false, e.getMessage()));
        }
        return this;
    }

    /**
     * Resolve-or-create the Company for this Adzuna result, then upsert the Job.
     * Returns the persisted Job, or null if we couldn't save it.
     * Broadcasts a NEW_JOB websocket event when a genuinely new job is inserted.
     */
    private Job upsertJob(Job incoming) {
        try {
            // Adapter stashed the employer name in `department`; use it as the
            // Company.name. Fallback to "Unknown (Adzuna)" if missing.
            String employerName = incoming.getDepartment();
            if (employerName == null || employerName.isBlank()) {
                employerName = "Unknown (Adzuna)";
            }
            Company company = resolveCompany(employerName);
            incoming.setCompany(company);
            incoming.setDepartment(null);                  // was just a carrier

            var existing = jobRepository.findByExternalIdAndCompanyId(
                    incoming.getExternalId(), company.getId());

            if (existing.isPresent()) {
                Job ex = existing.get();
                ex.setTitle(incoming.getTitle());
                ex.setLocation(incoming.getLocation());
                ex.setDescription(incoming.getDescription());
                ex.setApplyUrl(incoming.getApplyUrl());
                ex.setActive(true);
                return jobRepository.save(ex);
            }
            Job saved = jobRepository.save(incoming);
            // Fire WS event for net-new jobs only — the UI uses this to
            // animate new cards into the list without a full refresh.
            jobUpdateHandler.broadcastNewJob(
                    saved.getId(), saved.getTitle(), company.getName());
            return saved;
        } catch (Exception e) {
            log.warn("Upsert failed for externalId={}: {}",
                    incoming.getExternalId(), e.getMessage());
            return null;
        }
    }

    /** Get-or-create a Company by display name. */
    private Company resolveCompany(String name) {
        return companyRepository.findByBoardType("ADZUNA").stream()
                .filter(c -> name.equalsIgnoreCase(c.getName()))
                .findFirst()
                .orElseGet(() -> companyRepository.save(Company.builder()
                        .name(name)
                        .boardType("ADZUNA")
                        .active(true)
                        .build()));
    }
}