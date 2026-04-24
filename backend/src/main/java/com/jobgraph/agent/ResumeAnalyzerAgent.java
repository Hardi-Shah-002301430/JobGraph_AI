package com.jobgraph.agent;

import com.jobgraph.message.AnalysisMessages.*;
import com.jobgraph.message.AnalysisMessages.AnalysisResult;
import com.jobgraph.message.AnalysisMessages.AnalyzeResume;
import com.jobgraph.message.PollingMessages.PollAdzuna;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.model.User;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.repository.UserRepository;
import com.jobgraph.service.ResumeParserService;

import akka.actor.typed.ActorRef;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.AbstractBehavior;
import akka.actor.typed.javadsl.ActorContext;
import akka.actor.typed.javadsl.Behaviors;
import akka.actor.typed.javadsl.Receive;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses uploaded resume via LLM, saves it, then immediately fans out one
 * PollAdzuna per preferred role — no waiting for the next scheduler tick.
 * Subsequent re-polls happen on the normal scheduler cadence.
 */
@Slf4j
public class ResumeAnalyzerAgent extends AbstractBehavior<AnalyzeResume> {

    private final ResumeParserService parserService;
    private final ResumeProfileRepository repository;
    private final UserRepository userRepository;
    private final ActorRef<PollAdzuna> pollerRef;

    private ResumeAnalyzerAgent(ActorContext<AnalyzeResume> ctx,
                                ResumeParserService parserService,
                                ResumeProfileRepository repository,
                                UserRepository userRepository,
                                ActorRef<PollAdzuna> pollerRef) {
        super(ctx);
        this.parserService  = parserService;
        this.repository     = repository;
        this.userRepository = userRepository;
        this.pollerRef      = pollerRef;
        log.info("ResumeAnalyzerAgent started");
    }

    public static Behavior<AnalyzeResume> create(ResumeParserService parser,
                                                  ResumeProfileRepository repo,
                                                  UserRepository userRepo,
                                                  ActorRef<PollAdzuna> pollerRef) {
        return Behaviors.setup(ctx ->
                new ResumeAnalyzerAgent(ctx, parser, repo, userRepo, pollerRef));
    }

    @Override
    public Receive<AnalyzeResume> createReceive() {
        return newReceiveBuilder()
                .onMessage(AnalyzeResume.class, this::onAnalyze)
                .build();
    }

    private Behavior<AnalyzeResume> onAnalyze(AnalyzeResume cmd) {
        try {
            User user = userRepository.findById(cmd.getUserId())
                    .orElseThrow(() -> new RuntimeException(
                            "Unknown userId: " + cmd.getUserId()));

            ResumeProfile profile = parserService.parse(cmd.getRawText());
            profile.setUser(user);
            ResumeProfile saved = repository.save(profile);

            log.info("Resume analysed: user={} resumeId={} name={} — {} skills, {} preferred roles",
                    user.getId(), saved.getId(), saved.getFullName(),
                    saved.getSkills().size(), saved.getPreferredRoles().size());

            // Immediately fan out one PollAdzuna per preferred role.
            // This bypasses the 15-min scheduler wait for first-time uploads.
            if (saved.getPreferredRoles() != null && !saved.getPreferredRoles().isEmpty()) {
                int fanned = 0;
                for (String role : saved.getPreferredRoles()) {
                    if (role == null || role.isBlank()) continue;
                    pollerRef.tell(new PollAdzuna(
                            user.getId(),
                            role,
                            getContext().getSystem().ignoreRef()));
                    fanned++;
                }
                log.info("ResumeAnalyzer triggered {} immediate PollAdzuna messages for user={}",
                        fanned, user.getId());
            }

            cmd.getReplyTo().tell(new AnalysisResult(
                    saved.getId(),
                    saved.getFullName(),
                    saved.getEmail(),
                    saved.getSummary(),
                    saved.getSkills(),
                    saved.getExperienceYears(),
                    saved.getEducationLevel(),
                    saved.getPreferredRoles(),
                    true, null));

        } catch (Exception e) {
            log.error("Resume analysis failed for userId={}", cmd.getUserId(), e);
            cmd.getReplyTo().tell(new AnalysisResult(
                    0L, null, null, null,
                    null, 0, null, null,
                    false, e.getMessage()));
        }
        return this;
    }
}