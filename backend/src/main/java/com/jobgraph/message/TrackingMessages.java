package com.jobgraph.message;

import com.jobgraph.model.ApplicationStatus;

import akka.actor.typed.ActorRef;
import lombok.Value;

public interface TrackingMessages {

    /** Marker for every message the TrackerAgent can receive. */
    interface TrackerCommand extends CborSerializable {}

    /** Update the status of an application. */
    @Value
    class UpdateStatus implements CborSerializable {
        long trackingId;
        ApplicationStatus newStatus;
        String notes;
        ActorRef<StatusUpdated> replyTo;
    }

    @Value
    class StatusUpdated implements CborSerializable {
        long trackingId;
        ApplicationStatus status;
        boolean success;
    }

    /** Classify an incoming email and update tracking if matched. */
    @Value
    class ClassifyEmail implements TrackerCommand {
        String subject;
        String sender;
        String bodySnippet;
        ActorRef<EmailClassified> replyTo;
    }

    @Value
    class EmailClassified implements CborSerializable {
        String classification;
        double confidence;
        Long matchedTrackingId; // nullable
    }

    /**
     * Sent by SchedulerAgent on its email-poll tick. Tells the TrackerAgent
     * to go hit IMAP for new mail for this user, fan out ClassifyEmail
     * messages to itself for each new message.
     */
    @Value
    class PollEmails implements TrackerCommand {
        long userId;
    }
}