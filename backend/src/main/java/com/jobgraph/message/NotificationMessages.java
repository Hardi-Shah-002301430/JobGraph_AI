package com.jobgraph.message;

import akka.actor.typed.ActorRef;
import lombok.Value;

public interface NotificationMessages {

    /** Send a Slack alert about new high-score matches. */
    @Value
    class NotifyNewMatches implements CborSerializable {
        long resumeId;
        int matchCount;
        double topScore;
        String topJobTitle;
    }

    /** Send a Slack alert about an application status change. */
    @Value
    class NotifyStatusChange implements CborSerializable {
        long trackingId;
        String jobTitle;
        String companyName;
        String oldStatus;
        String newStatus;
    }

    /** Generic Slack message. */
    @Value
    class SendSlack implements CborSerializable {
        String channel;
        String text;
        ActorRef<SlackSent> replyTo;
    }

    @Value
    class SlackSent implements CborSerializable {
        boolean success;
        String errorMessage;
    }
}
