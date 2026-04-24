package com.jobgraph.repository;

import com.jobgraph.model.SeenEmail;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SeenEmailRepository extends JpaRepository<SeenEmail, Long> {

    /** Cheap existence check — we query this once per incoming message. */
    boolean existsByUserIdAndMessageId(Long userId, String messageId);
}