package com.jobgraph.controller;

import com.jobgraph.dto.response.DashboardStatsResponse;
import com.jobgraph.dto.response.JobMatchResponse;
import com.jobgraph.dto.response.PageResponse;
import com.jobgraph.model.ApplicationStatus;
import com.jobgraph.model.MatchScore;
import com.jobgraph.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Slf4j
public class JobController {

    private final JobRepository jobRepository;
    private final MatchScoreRepository matchRepository;
    private final CompanyRepository companyRepository;
    private final ApplicationTrackingRepository trackingRepository;

    /** Default user while auth isn't wired up. Migration seeds this row. */
    private static final long DEFAULT_USER_ID = 1L;

    /**
     * Top job matches aggregated across every resume the user owns.
     * The returned rows include which resume produced each score so the UI
     * can surface "matched against your DevOps resume" etc.
     */
    @GetMapping("/matches")
    public ResponseEntity<PageResponse<JobMatchResponse>> matches(
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        var pageable = PageRequest.of(page, size, Sort.by("overallScore").descending());
        var matchPage = matchRepository.findTopMatchesForUser(userId, pageable);

        var responsePage = matchPage.map(this::toMatchResponse);
        return ResponseEntity.ok(PageResponse.of(responsePage));
    }

    /** Dashboard stats, aggregated across all resumes a user owns. */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardStatsResponse> dashboard(
            @RequestParam(value = "userId", defaultValue = "" + DEFAULT_USER_ID) Long userId) {

        long totalJobs = jobRepository.countActive();
        long totalCompanies = companyRepository.count();
        long totalMatches = matchRepository.countByUserId(userId);
        long totalApplications = trackingRepository.countByUserId(userId);
        Double avgScore = matchRepository.averageScoreForUser(userId);

        Map<String, Long> byStatus = new HashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        trackingRepository.countByStatusForUser(userId).forEach(row ->
                byStatus.put(((ApplicationStatus) row[0]).name(), (Long) row[1]));

        return ResponseEntity.ok(DashboardStatsResponse.builder()
                .totalJobs(totalJobs)
                .totalCompanies(totalCompanies)
                .totalMatches(totalMatches)
                .totalApplications(totalApplications)
                .averageMatchScore(avgScore != null ? avgScore : 0.0)
                .applicationsByStatus(byStatus)
                .topMatchScore(fetchTopScore(userId))
                .build());
    }

    private Double fetchTopScore(Long userId) {
        var pageable = PageRequest.of(0, 1, Sort.by("overallScore").descending());
        var top = matchRepository.findTopMatchesForUser(userId, pageable);
        return top.hasContent() ? top.getContent().get(0).getOverallScore() : 0.0;
    }

    private JobMatchResponse toMatchResponse(MatchScore m) {
        var job = m.getJob();
        var resume = m.getResume();
        return JobMatchResponse.builder()
                .jobId(job.getId())
                .jobTitle(job.getTitle())
                .companyName(job.getCompany() != null ? job.getCompany().getName() : null)
                .location(job.getLocation())
                .employmentType(job.getEmploymentType())
                .postedAt(job.getPostedAt())
                .resumeId(resume != null ? resume.getId() : null)
                .resumeLabel(resume != null ? resume.getDisplayLabel() : null)
                .overallScore(m.getOverallScore())
                .skillScore(m.getSkillScore())
                .experienceScore(m.getExperienceScore())
                .educationScore(m.getEducationScore())
                .industryScore(m.getIndustryScore())
                .locationScore(m.getLocationScore())
                .skillDetail(m.getSkillDetail())
                .experienceDetail(m.getExperienceDetail())
                .educationDetail(m.getEducationDetail())
                .industryDetail(m.getIndustryDetail())
                .locationDetail(m.getLocationDetail())
                .resumeTips(m.getResumeTips())
                .build();
    }
}