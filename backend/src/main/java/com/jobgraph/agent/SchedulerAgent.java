package com.jobgraph.agent;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobgraph.cluster.ClusterManager;
import com.jobgraph.message.PollingMessages.PollAdzuna;
import com.jobgraph.message.SchedulerMessages.Tick;
import com.jobgraph.message.TrackingMessages.PollEmails;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.model.User;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cluster singleton: runs exactly once across all nodes. On each tick it
 * iterates every user and, for each unique role in any of their resumes'
 * preferredRoles, tells the PollerAgent to search Adzuna.
 *
 * Because the PollerAgent ref is a pool router, the fanned-out messages
 * are distributed across worker actors for real parallelism.
 */
@Slf4j
public class SchedulerAgent extends AbstractBehavior<Tick> {

    private final UserRepository userRepository;
    private final ResumeProfileRepository resumeRepository;
    private final ClusterManager clusterManager;
    private boolean running = false;

    private SchedulerAgent(ActorContext<Tick> ctx,
                           UserRepository userRepository,
                           ResumeProfileRepository resumeRepository,
                           ClusterManager clusterManager,
                           int intervalMinutes) {
        super(ctx);
        this.userRepository = userRepository;
        this.resumeRepository = resumeRepository;
        this.clusterManager = clusterManager;

        // Kick off a periodic tick. First tick after 10s so startup has time
        // to settle; subsequent ticks every `intervalMinutes` minutes.
        ctx.getSystem().scheduler().scheduleAtFixedRate(
                Duration.ofSeconds(10),
                Duration.ofMinutes(intervalMinutes),
                () -> ctx.getSelf().tell(new Tick()),
                ctx.getExecutionContext());

        log.info("SchedulerAgent started (singleton) — ticking every {} min", intervalMinutes);
    }

    public static Behavior<Tick> create(UserRepository users,
                                         ResumeProfileRepository resumes,
                                         ClusterManager cm,
                                         int intervalMinutes) {
        return Behaviors.setup(ctx ->
                new SchedulerAgent(ctx, users, resumes, cm, intervalMinutes));
    }

    @Override
    public Receive<Tick> createReceive() {
        return newReceiveBuilder()
                .onMessage(Tick.class, this::onTick)
                .build();
    }

    private Behavior<Tick> onTick(Tick tick) {
        if (running) {
            log.info("Tick ignored — previous cycle still running");
            return this;
        }
        running = true;

        try {
            List<User> users = userRepository.findAll();
            log.info("Scheduler tick — {} users", users.size());

            int fanout = 0;
            int emailPolls = 0;
            for (User user : users) {
                // Tell TrackerAgent to check this user's inbox for new mail.
                // The tracker handles the IMAP-disabled case internally, so
                // we can fire this unconditionally.
                clusterManager.getTrackerRef().tell(new PollEmails(user.getId()));
                emailPolls++;

                List<ResumeProfile> resumes =
                        resumeRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
                if (resumes.isEmpty()) continue;

                // Union of preferredRoles across all the user's resumes.
                // No need to poll "Platform Engineer" twice for the same user.
                Set<String> roles = new HashSet<>();
                for (ResumeProfile r : resumes) {
                    if (r.getPreferredRoles() != null) {
                        roles.addAll(r.getPreferredRoles());
                    }
                }

                for (String role : roles) {
                    if (role == null || role.isBlank()) continue;
                    clusterManager.getPollerRef().tell(new PollAdzuna(
                            user.getId(),
                            role,
                            getContext().getSystem().ignoreRef()));
                    fanout++;
                }
            }

            log.info("Scheduler fanned out {} PollAdzuna + {} PollEmails messages",
                    fanout, emailPolls);
        } catch (Exception e) {
            log.error("Scheduler tick failed", e);
        } finally {
            running = false;
        }
        return this;
    }
}