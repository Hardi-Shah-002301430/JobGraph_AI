package com.jobgraph.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobgraph.model.ApplicationStatus;
import com.jobgraph.model.ApplicationTracking;

/**
 * Tracking rows are still per (job, resume) — that's how the matcher writes
 * them. The read-side aggregates by user for the dashboard.
 */
public interface ApplicationTrackingRepository extends JpaRepository<ApplicationTracking, Long> {

    Optional<ApplicationTracking> findByJobIdAndResumeId(Long jobId, Long resumeId);

    List<ApplicationTracking> findByStatus(ApplicationStatus status);

    /** All tracking rows owned by any of the user's resumes, newest first. */
    @Query("""
           SELECT t FROM ApplicationTracking t
           JOIN FETCH t.job j
           JOIN FETCH j.company
           JOIN FETCH t.resume r
           WHERE r.user.id = :userId
           ORDER BY t.updatedAt DESC
           """)
    List<ApplicationTracking> findByUserIdOrderByUpdatedAtDesc(@Param("userId") Long userId);

    /** Status → count, for the dashboard's "applications by status" widget. */
    @Query("""
           SELECT t.status, COUNT(t) FROM ApplicationTracking t
           WHERE t.resume.user.id = :userId
           GROUP BY t.status
           """)
    List<Object[]> countByStatusForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(t) FROM ApplicationTracking t WHERE t.resume.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);

    /**
     * Find open (i.e., applied but not yet concluded) tracking rows for a user
     * whose job.company.name case-insensitively contains the given company
     * fragment. Used by the email tracker to match an incoming email to an
     * application the user has actually submitted.
     *
     * "Concluded" statuses (REJECTED, ACCEPTED, WITHDRAWN) are excluded — we
     * don't reopen a closed application.
     */
    @Query("""
           SELECT t FROM ApplicationTracking t
           WHERE t.resume.user.id = :userId
             AND LOWER(t.job.company.name) LIKE LOWER(CONCAT('%', :company, '%'))
             AND t.status NOT IN (com.jobgraph.model.ApplicationStatus.REJECTED,
                                  com.jobgraph.model.ApplicationStatus.ACCEPTED,
                                  com.jobgraph.model.ApplicationStatus.WITHDRAWN)
           ORDER BY t.updatedAt DESC
           """)
    List<ApplicationTracking> findOpenByUserAndCompany(@Param("userId") Long userId,
                                                       @Param("company") String company);
}