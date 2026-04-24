package com.jobgraph.repository;

import com.jobgraph.model.JobAlert;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobAlertRepository extends JpaRepository<JobAlert, Long> {
    boolean existsByUserIdAndJobId(Long userId, Long jobId);
}