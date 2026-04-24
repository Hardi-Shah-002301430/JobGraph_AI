package com.jobgraph.repository;

import com.jobgraph.model.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long> {

    Optional<Job> findByExternalIdAndCompanyId(String externalId, Long companyId);

    Page<Job> findByActiveTrue(Pageable pageable);

    @Query("SELECT j FROM Job j WHERE j.active = true AND j.company.id = :companyId")
    List<Job> findActiveByCompany(@Param("companyId") Long companyId);

    @Query("SELECT j FROM Job j WHERE j.active = true " +
           "AND LOWER(j.title) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    Page<Job> searchByTitle(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(j) FROM Job j WHERE j.active = true")
    long countActive();
}
