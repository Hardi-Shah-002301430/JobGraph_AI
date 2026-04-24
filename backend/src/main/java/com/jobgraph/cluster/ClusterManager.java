package com.jobgraph.cluster;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.jobgraph.agent.MatchAnalyzerAgent;
import com.jobgraph.agent.NotificationAgent;
import com.jobgraph.agent.PollerAgent;
import com.jobgraph.agent.ResumeAdvisorAgent;
import com.jobgraph.agent.ResumeAnalyzerAgent;
import com.jobgraph.agent.SchedulerAgent;
import com.jobgraph.agent.TrackerAgent;
import com.jobgraph.config.AppProperties;
import com.jobgraph.dto.response.ClusterStatusResponse;
import com.jobgraph.message.AdvisorMessages;
import com.jobgraph.message.AnalysisMessages;
import com.jobgraph.message.MatchMessages;
import com.jobgraph.message.NotificationMessages;
import com.jobgraph.message.PollingMessages;
import com.jobgraph.message.SchedulerMessages;
import com.jobgraph.message.TrackingMessages;
import com.jobgraph.repository.ApplicationTrackingRepository;
import com.jobgraph.repository.CompanyRepository;
import com.jobgraph.repository.EmailClassificationRepository;
import com.jobgraph.repository.JobAlertRepository;
import com.jobgraph.repository.JobRepository;
import com.jobgraph.repository.MatchScoreRepository;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.repository.UserRepository;
import com.jobgraph.service.EmailService;
import com.jobgraph.service.JobBoardAdapter;
import com.jobgraph.service.LlmService;
import com.jobgraph.service.ResumeParserService;
import com.jobgraph.service.SlackService;
import com.jobgraph.websocket.JobUpdateHandler;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.Props;
import akka.actor.typed.javadsl.Routers;
import akka.cluster.Member;
import akka.cluster.typed.Cluster;
import akka.cluster.typed.ClusterSingleton;
import akka.cluster.typed.SingletonActor;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Cluster topology:
 *   SchedulerAgent  → Cluster Singleton (exactly one across all nodes)
 *   MatchAnalyzer   → Pool of 4 (round-robin parallelism for LLM scoring)
 *   PollerAgent     → Pool of 4 (parallel across roles/keywords)
 *   Everything else → single local actor (low volume or stateful)
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ClusterManager {

    private static final int MATCH_POOL_SIZE = 1;
    private static final int POLL_POOL_SIZE  = 1;

    private final ActorSystem<Void> actorSystem;
    private final LlmService llmService;
    private final SlackService slackService;
    private final EmailService emailService;
    private final ResumeParserService resumeParserService;
    private final List<JobBoardAdapter> boardAdapters;
    private final AppProperties appProperties;
    private final JobUpdateHandler jobUpdateHandler;

    // Repositories
    private final JobRepository jobRepository;
    private final CompanyRepository companyRepository;
    private final MatchScoreRepository matchScoreRepository;
    private final ResumeProfileRepository resumeProfileRepository;
    private final ApplicationTrackingRepository trackingRepository;
    private final EmailClassificationRepository emailClassificationRepository;
    private final UserRepository userRepository;
    private final JobAlertRepository jobAlertRepository;

    @Getter private ActorRef<SchedulerMessages.Tick> schedulerRef;
    @Getter private ActorRef<PollingMessages.PollAdzuna> pollerRef;
    @Getter private ActorRef<AnalysisMessages.AnalyzeResume> resumeAnalyzerRef;
    @Getter private ActorRef<MatchMessages.ScoreJob> matchAnalyzerRef;
    @Getter private ActorRef<AdvisorMessages.AdviseForJob> resumeAdvisorRef;
    @Getter private ActorRef<NotificationMessages.SendSlack> notificationRef;
    @Getter private ActorRef<TrackingMessages.TrackerCommand> trackerRef;

    @PostConstruct
    public void bootstrap() {
        log.info("Bootstrapping Akka agent cluster…");

        // ── NotificationAgent + ResumeAdvisor first ───────────────────
        // Both are dependencies of the MatchAnalyzer pool, so they must
        // be spawned before the pool is built.
        notificationRef = actorSystem.systemActorOf(
                NotificationAgent.create(slackService),
                ClusterConstants.NOTIFICATION, Props.empty());

        resumeAdvisorRef = actorSystem.systemActorOf(
                ResumeAdvisorAgent.create(llmService, matchScoreRepository,
                        jobRepository, resumeProfileRepository),
                ClusterConstants.RESUME_ADVISOR, Props.empty());

        // ── MatchAnalyzer: pool of 4 workers ──────────────────────────
        // Each worker does one LLM call at a time; 4 in parallel respects
        // Groq's free-tier rate limit (~30 req/min).
        Behavior<MatchMessages.ScoreJob> matchPool = Routers.pool(
                MATCH_POOL_SIZE,
                MatchAnalyzerAgent.create(
                        llmService,
                        jobRepository,
                        resumeProfileRepository,
                        matchScoreRepository,
                        jobAlertRepository,
                        notificationRef,
                        resumeAdvisorRef,
                        jobUpdateHandler,
                        appProperties.getMatching().getAlertThreshold()))
                .withRoundRobinRouting();
        matchAnalyzerRef = actorSystem.systemActorOf(
                matchPool, ClusterConstants.MATCH_ANALYZER, Props.empty());

        // ── Poller: pool of 4 workers ─────────────────────────────────
        // Each worker handles one PollAdzuna message (one role for one user).
        // 4 roles poll in parallel instead of serially.
        Behavior<PollingMessages.PollAdzuna> pollerPool = Routers.pool(
                POLL_POOL_SIZE,
                PollerAgent.create(
                        boardAdapters,
                        jobRepository,
                        companyRepository,
                        resumeProfileRepository,
                        matchAnalyzerRef,
                        jobUpdateHandler))
                .withRoundRobinRouting();
        pollerRef = actorSystem.systemActorOf(
                pollerPool, ClusterConstants.POLLER_AGENT, Props.empty());

        // ── Scheduler: cluster singleton ──────────────────────────────
        // Guaranteed exactly-once across the cluster. If the host node dies,
        // another node takes over automatically.
        ClusterSingleton singleton = ClusterSingleton.get(actorSystem);
        schedulerRef = singleton.init(SingletonActor.of(
                SchedulerAgent.create(
                        userRepository,
                        resumeProfileRepository,
                        this,
                        appProperties.getPolling().getIntervalMinutes()),
                ClusterConstants.SCHEDULER_AGENT));

        // ── Single-instance local actors (not perf-critical) ──────────
        resumeAnalyzerRef = actorSystem.systemActorOf(
                ResumeAnalyzerAgent.create(resumeParserService,
                        resumeProfileRepository, userRepository, pollerRef),
                ClusterConstants.RESUME_ANALYZER, Props.empty());

        trackerRef = actorSystem.systemActorOf(
                TrackerAgent.create(llmService, emailService,
                        trackingRepository, emailClassificationRepository,
                        jobUpdateHandler, appProperties),
                ClusterConstants.TRACKER, Props.empty());

        log.info("Cluster topology ready — scheduler singleton, "
                + "match pool={}, poll pool={}, alert threshold={}",
                MATCH_POOL_SIZE, POLL_POOL_SIZE,
                appProperties.getMatching().getAlertThreshold());
    }

    /** Snapshot of current cluster membership for the /cluster endpoint. */
    public ClusterStatusResponse getClusterStatus() {
        Cluster cluster = Cluster.get(actorSystem);
        var state = cluster.state();

        List<ClusterStatusResponse.NodeInfo> nodes = new ArrayList<>();
        for (Member m : state.getMembers()) {
            nodes.add(ClusterStatusResponse.NodeInfo.builder()
                    .address(m.address().toString())
                    .status(m.status().toString())
                    .roles(new ArrayList<>(m.getRoles()))
                    .build());
        }

        String leader = state.getLeader() != null ? state.getLeader().toString() : "none";

        return ClusterStatusResponse.builder()
                .totalNodes(nodes.size())
                .upNodes((int) nodes.stream()
                        .filter(n -> "Up".equals(n.getStatus()))
                        .count())
                .selfAddress(cluster.selfMember().address().toString())
                .leaderAddress(leader)
                .nodes(nodes)
                .build();
    }
}