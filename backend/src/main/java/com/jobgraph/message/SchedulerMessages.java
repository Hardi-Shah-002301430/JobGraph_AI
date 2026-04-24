package com.jobgraph.message;

import akka.actor.typed.ActorRef;
import lombok.Value;

public interface SchedulerMessages {

    /** Kick off a full polling cycle for all active companies. */
    @Value
    class StartPollingCycle implements CborSerializable {
        long triggeredBy; // resumeId or 0 for scheduled
    }

    /** Kick off match analysis for newly scraped jobs. */
    @Value
    class StartMatchCycle implements CborSerializable {
        long resumeId;
    }

    /** A single tick from the internal timer. */
    @Value
    class Tick implements CborSerializable {}

    /** Acknowledge that an agent finished its work. */
    @Value
    class AgentDone implements CborSerializable {
        String agentName;
        int itemsProcessed;
    }

    /** Request current scheduler status. */
    @Value
    class GetStatus implements CborSerializable {
        ActorRef<StatusReply> replyTo;
    }

    @Value
    class StatusReply implements CborSerializable {
        boolean running;
        long lastCycleEpoch;
        int companiesQueued;
    }
}
