package com.jobgraph.repository;

import com.jobgraph.model.Company;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyRepository extends JpaRepository<Company, Long> {

    List<Company> findByActiveTrue();

    List<Company> findByBoardType(String boardType);
}
