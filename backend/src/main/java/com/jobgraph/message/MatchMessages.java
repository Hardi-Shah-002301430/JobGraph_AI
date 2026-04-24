package com.jobgraph.message;

import akka.actor.typed.ActorRef;
import lombok.Value;

public interface MatchMessages {

    /** Score a single job against a resume profile. */
    @Value
    class ScoreJob implements CborSerializable {
        long jobId;
        long resumeId;
        ActorRef<ScoreResult> replyTo;
    }

    /** Batch-score a list of jobs. */
    @Value
    class ScoreBatch implements CborSerializable {
        java.util.List<Long> jobIds;
        long resumeId;
        ActorRef<BatchScoreResult> replyTo;
    }

    @Value
    class ScoreResult implements CborSerializable {
        long jobId;
        long resumeId;
        double overallScore;
        double skillScore;
        double experienceScore;
        double educationScore;
        double industryScore;
        double locationScore;
        String skillDetail;
        String experienceDetail;
        String educationDetail;
        String industryDetail;
        String locationDetail;
        boolean success;
        String errorMessage;
    }

    @Value
    class BatchScoreResult implements CborSerializable {
        int scored;
        int failed;
    }
}
