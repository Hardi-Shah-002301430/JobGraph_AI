package com.jobgraph.message;

import akka.actor.typed.ActorRef;
import lombok.Value;

import java.util.List;

public interface AnalysisMessages {

    /** Analyse raw resume text and extract structured profile for a user. */
    @Value
    class AnalyzeResume implements CborSerializable {
        long userId;
        String rawText;
        ActorRef<AnalysisResult> replyTo;
    }

    /** Result of resume analysis. */
    @Value
    class AnalysisResult implements CborSerializable {
        long resumeId;
        String fullName;
        String email;
        String summary;
        List<String> skills;
        int experienceYears;
        String educationLevel;
        List<String> preferredRoles;
        boolean success;
        String errorMessage;
    }
}