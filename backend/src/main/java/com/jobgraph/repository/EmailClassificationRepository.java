package com.jobgraph.repository;

import com.jobgraph.model.EmailClassification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Audit trail of every email the TrackerAgent has classified. Useful for
 * debugging ("why did the LLM think this was a rejection?") and for
 * rendering a history view in the UI next to each tracked application.
 */
public interface EmailClassificationRepository extends JpaRepository<EmailClassification, Long> {

    /** Classifications attached to one tracking row, newest first. */
    @Query("""
           SELECT e FROM EmailClassification e
           WHERE e.tracking.id = :trackingId
           ORDER BY e.classifiedAt DESC
           """)
    List<EmailClassification> findByTrackingId(@Param("trackingId") Long trackingId);

    /** Every classification for a user's applications, newest first. */
    @Query("""
           SELECT e FROM EmailClassification e
           WHERE e.tracking.resume.user.id = :userId
           ORDER BY e.classifiedAt DESC
           """)
    List<EmailClassification> findByUserId(@Param("userId") Long userId);
}