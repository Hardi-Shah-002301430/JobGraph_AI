package com.jobgraph.cluster;

/**
 * Constants shared across the Akka Cluster: actor names, roles, service keys.
 */
public final class ClusterConstants {

    private ClusterConstants() {}

    // ── Singleton actor names (cluster-wide) ──
    public static final String SCHEDULER_AGENT  = "scheduler-agent";
    public static final String POLLER_AGENT     = "poller-agent";

    // ── Pool / per-request actors ──
    public static final String RESUME_ANALYZER  = "resume-analyzer";
    public static final String MATCH_ANALYZER   = "match-analyzer";
    public static final String RESUME_ADVISOR   = "resume-advisor";
    public static final String NOTIFICATION     = "notification-agent";
    public static final String TRACKER          = "tracker-agent";

    // ── Cluster roles ──
    public static final String ROLE_SCHEDULER = "scheduler";
    public static final String ROLE_WORKER    = "worker";
    public static final String ROLE_API       = "api";
}