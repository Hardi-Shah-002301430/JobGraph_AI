package com.jobgraph.controller;

import com.jobgraph.cluster.ActorBridge;
import com.jobgraph.cluster.ClusterManager;
import com.jobgraph.dto.request.ResumeUploadRequest;
import com.jobgraph.dto.response.ResumeProfileResponse;
import com.jobgraph.message.AnalysisMessages;
import com.jobgraph.message.AnalysisMessages.AnalyzeResume;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.model.User;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.repository.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletionStage;

@RestController
@RequestMapping("/api/resumes")
@RequiredArgsConstructor
@Slf4j
public class ResumeController {

    private final ClusterManager clusterManager;
    private final ActorBridge actorBridge;
    private final ResumeProfileRepository resumeRepository;
    private final UserRepository userRepository;

    /** Default user id while auth isn't wired up. Migration seeds this row. */
    private static final long DEFAULT_USER_ID = 1L;

    /**
     * Upload a resume (PDF or text). Associated with userId from the query
     * string, or the default user if omitted.
     *
     *   curl -F "file=@resume.pdf" "http://localhost:8080/api/resumes?userId=1"
     */
    @PostMapping(consumes = { "multipart/form-data" })
    public CompletionStage<ResponseEntity<ResumeProfileResponse>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId)
            throws Exception {

        if (file.isEmpty()) {
            throw new IllegalArgumentException("Uploaded file is empty");
        }
        User user = resolveUser(userId);
        String text = extractText(file);
        log.info("Extracted {} chars from {} for user={}",
                text.length(), file.getOriginalFilename(), user.getId());

        if (text.isBlank()) {
            throw new IllegalArgumentException("Could not extract any text from file");
        }
        return analyze(text, user);
    }

    @PostMapping(consumes = { "application/json" })
    public CompletionStage<ResponseEntity<ResumeProfileResponse>> uploadJson(
            @Valid @RequestBody ResumeUploadRequest request,
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId) {
        User user = resolveUser(userId);
        return analyze(request.getRawText(), user);
    }

    /** List every resume owned by a user — used to populate the UI dropdown. */
    @GetMapping
    public List<ResumeProfileResponse> list(
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId) {
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ResumeProfileResponse> getById(@PathVariable Long id) {
        return resumeRepository.findById(id)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/latest")
    public ResponseEntity<ResumeProfileResponse> getLatest(
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId) {
        return resumeRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
                .map(this::toResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ──────────────────────────────────────────────────────────

    private User resolveUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown user: " + userId));
    }

    private String extractText(MultipartFile file) throws Exception {
        String name = file.getOriginalFilename();
        String contentType = file.getContentType();
        boolean isPdf =
                (contentType != null && contentType.toLowerCase().contains("pdf"))
                || (name != null && name.toLowerCase().endsWith(".pdf"));

        if (isPdf) {
            try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                stripper.setSortByPosition(true);
                return stripper.getText(doc);
            }
        }
        return new String(file.getBytes(), StandardCharsets.UTF_8);
    }

    private CompletionStage<ResponseEntity<ResumeProfileResponse>> analyze(String rawText, User user) {
        return actorBridge.<AnalyzeResume, AnalysisMessages.AnalysisResult>ask(
                clusterManager.getResumeAnalyzerRef(),
                replyTo -> new AnalyzeResume(user.getId(), rawText, replyTo)
        ).thenApply(result -> {
            if (!result.isSuccess()) {
                log.error("Resume analysis failed: {}", result.getErrorMessage());
                return ResponseEntity.internalServerError().<ResumeProfileResponse>build();
            }
            return ResponseEntity.ok(ResumeProfileResponse.builder()
                    .id(result.getResumeId())
                    .fullName(result.getFullName())
                    .email(result.getEmail())
                    .summary(result.getSummary())
                    .skills(result.getSkills())
                    .experienceYears(result.getExperienceYears())
                    .educationLevel(result.getEducationLevel())
                    .preferredRoles(result.getPreferredRoles())
                    .build());
        });
    }

    private ResumeProfileResponse toResponse(ResumeProfile r) {
        return ResumeProfileResponse.builder()
                .id(r.getId())
                .fullName(r.getFullName())
                .email(r.getEmail())
                .phone(r.getPhone())
                .summary(r.getSummary())
                .skills(r.getSkills())
                .experienceYears(r.getExperienceYears())
                .educationLevel(r.getEducationLevel())
                .preferredRoles(r.getPreferredRoles())
                .createdAt(r.getCreatedAt())
                .build();
    }
}