package com.jobgraph.controller;

import com.jobgraph.dto.request.CompanyCreateRequest;
import com.jobgraph.dto.response.CompanyResponse;
import com.jobgraph.model.Company;
import com.jobgraph.repository.CompanyRepository;
import com.jobgraph.repository.JobRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyRepository companyRepository;
    private final JobRepository jobRepository;

    @GetMapping
    public List<CompanyResponse> list() {
        return companyRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @PostMapping
    public ResponseEntity<CompanyResponse> create(@Valid @RequestBody CompanyCreateRequest req) {
        Company company = Company.builder()
                .name(req.getName())
                .careersUrl(req.getCareersUrl())
                .boardType(req.getBoardType())
                .logoUrl(req.getLogoUrl())
                .industry(req.getIndustry())
                .active(true)
                .build();
        Company saved = companyRepository.save(company);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PatchMapping("/{id}/toggle")
    public ResponseEntity<CompanyResponse> toggleActive(@PathVariable Long id) {
        return companyRepository.findById(id)
                .map(c -> {
                    c.setActive(!c.getActive());
                    companyRepository.save(c);
                    return ResponseEntity.ok(toResponse(c));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!companyRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        companyRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private CompanyResponse toResponse(Company c) {
        long jobCount = jobRepository.findActiveByCompany(c.getId()).size();
        return CompanyResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .careersUrl(c.getCareersUrl())
                .boardType(c.getBoardType())
                .logoUrl(c.getLogoUrl())
                .industry(c.getIndustry())
                .active(c.getActive())
                .jobCount(jobCount)
                .build();
    }
}
