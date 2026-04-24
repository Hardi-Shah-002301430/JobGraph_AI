package com.jobgraph.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jobgraph.model.MatchScore;

/**
 * All queries are user-scoped. A user may own multiple resumes; we aggregate
 * across them. The (job, resume) lookup is kept because the matcher still
 * writes one score per (job, resume) pair — only the read-side is aggregated.
 */
public interface MatchScoreRepository extends JpaRepository<MatchScore, Long> {

    /** Used by the matcher to upsert a score for a (job, resume) pair. */
    Optional<MatchScore> findByJobIdAndResumeId(Long jobId, Long resumeId);

    /** Top matches across every resume the user owns, with job+resume eagerly fetched. */
    @Query(value = """
           SELECT m FROM MatchScore m
           JOIN FETCH m.job j
           JOIN FETCH j.company
           JOIN FETCH m.resume r
           WHERE r.user.id = :userId
           ORDER BY m.overallScore DESC
           """,
           countQuery = """
           SELECT COUNT(m) FROM MatchScore m
           WHERE m.resume.user.id = :userId
           """)
    Page<MatchScore> findTopMatchesForUser(@Param("userId") Long userId, Pageable pageable);

    /** Every score for this user against this job — used by alert fanout. */
    @Query("""
           SELECT m FROM MatchScore m
           WHERE m.job.id = :jobId AND m.resume.user.id = :userId
           """)
    List<MatchScore> findByJobIdAndUserId(@Param("jobId") Long jobId,
                                          @Param("userId") Long userId);

    @Query("SELECT AVG(m.overallScore) FROM MatchScore m WHERE m.resume.user.id = :userId")
    Double averageScoreForUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(m) FROM MatchScore m WHERE m.resume.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
}