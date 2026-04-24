package com.jobgraph.repository;

import com.jobgraph.model.ResumeProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Resumes are always owned by a user; every read scopes by userId.
 * The "latest" concept is now "latest for this user" — there is no
 * global-latest because that would leak across users in a multi-user system.
 */
public interface ResumeProfileRepository extends JpaRepository<ResumeProfile, Long> {

    Optional<ResumeProfile> findByEmail(String email);

    Optional<ResumeProfile> findTopByUserIdOrderByCreatedAtDesc(Long userId);

    List<ResumeProfile> findByUserIdOrderByCreatedAtDesc(Long userId);
}