package com.jobgraph.message;

import akka.actor.typed.ActorRef;
import lombok.Value;

import java.util.List;

public interface AdvisorMessages {

    /** Generate resume improvement tips for a specific job match. */
    @Value
    class AdviseForJob implements CborSerializable {
        long matchScoreId;
        long jobId;
        long resumeId;
        ActorRef<AdviceResult> replyTo;
    }

    @Value
    class AdviceResult implements CborSerializable {
        long matchScoreId;
        List<String> tips;
        List<String> missingSkills;
        String rewrittenSummary;
        boolean success;
        String errorMessage;
    }
}
