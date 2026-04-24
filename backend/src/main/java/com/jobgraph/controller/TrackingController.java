package com.jobgraph.controller;

import com.jobgraph.dto.request.StatusUpdateRequest;
import com.jobgraph.dto.response.TrackingResponse;
import com.jobgraph.model.ApplicationStatus;
import com.jobgraph.model.ApplicationTracking;
import com.jobgraph.model.Job;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.repository.ApplicationTrackingRepository;
import com.jobgraph.repository.JobRepository;
import com.jobgraph.repository.MatchScoreRepository;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.websocket.JobUpdateHandler;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tracking")
@RequiredArgsConstructor
public class TrackingController {

    private final ApplicationTrackingRepository trackingRepository;
    private final JobRepository jobRepository;
    private final ResumeProfileRepository resumeRepository;
    private final MatchScoreRepository matchRepository;
    private final JobUpdateHandler jobUpdateHandler;

    private static final long DEFAULT_USER_ID = 1L;

    /** All tracking rows owned by any of this user's resumes, newest first. */
    @GetMapping
    public List<TrackingResponse> listForUser(
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId) {
        return trackingRepository.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Bookmark a job against a specific resume. The tracking row carries
     * which resume was used, so the user can track the same job twice if
     * they want to apply with different resume variants.
     */
    @PostMapping
    public ResponseEntity<TrackingResponse> startTracking(
            @RequestParam Long jobId,
            @RequestParam Long resumeId) {

        var existing = trackingRepository.findByJobIdAndResumeId(jobId, resumeId);
        if (existing.isPresent()) {
            return ResponseEntity.ok(toResponse(existing.get()));
        }

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));
        ResumeProfile resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new RuntimeException("Resume not found"));

        ApplicationTracking t = ApplicationTracking.builder()
                .job(job)
                .resume(resume)
                .status(ApplicationStatus.BOOKMARKED)
                .updatedAt(Instant.now())
                .build();

        return ResponseEntity.ok(toResponse(trackingRepository.save(t)));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<TrackingResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody StatusUpdateRequest req) {

        return trackingRepository.findById(id)
                .map(t -> {
                    t.setStatus(req.getStatus());
                    if (req.getNotes() != null) t.setNotes(req.getNotes());
                    if (req.getStatus() == ApplicationStatus.APPLIED && t.getAppliedAt() == null) {
                        t.setAppliedAt(Instant.now());
                    }
                    t.setUpdatedAt(Instant.now());
                    ApplicationTracking saved = trackingRepository.save(t);
                    jobUpdateHandler.broadcastStatusChange(saved.getId(), saved.getStatus().name());
                    return ResponseEntity.ok(toResponse(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private TrackingResponse toResponse(ApplicationTracking t) {
        Double matchScore = matchRepository
                .findByJobIdAndResumeId(t.getJob().getId(), t.getResume().getId())
                .map(m -> m.getOverallScore())
                .orElse(null);

        return TrackingResponse.builder()
                .id(t.getId())
                .jobId(t.getJob().getId())
                .jobTitle(t.getJob().getTitle())
                .companyName(t.getJob().getCompany() != null ? t.getJob().getCompany().getName() : null)
                .status(t.getStatus().name())
                .notes(t.getNotes())
                .appliedAt(t.getAppliedAt())
                .updatedAt(t.getUpdatedAt())
                .matchScore(matchScore)
                .build();
    }
}