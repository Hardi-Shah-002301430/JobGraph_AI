package com.jobgraph.message;

import akka.actor.typed.ActorRef;
import lombok.Value;

import java.util.List;

public interface PollingMessages {

    /**
     * Poll Adzuna for jobs matching a role, on behalf of a user.
     * The PollerAgent fetches + scrapes + upserts jobs + fans out to the matcher.
     */
    @Value
    class PollAdzuna implements CborSerializable {
        long userId;
        String role;                           // e.g. "Platform Engineer"
        ActorRef<PollResult> replyTo;
    }

    /** Result of one PollAdzuna run. */
    @Value
    class PollResult implements CborSerializable {
        long userId;
        String role;
        int jobsFetched;
        int jobsScored;
        boolean success;
        String errorMessage;
    }
}