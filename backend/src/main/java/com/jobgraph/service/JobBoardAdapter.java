package com.jobgraph.service;

import com.jobgraph.model.Job;

import java.util.List;

/**
 * Common contract for all job-board adapters.
 * Each adapter knows how to call a specific board API
 * (Ashby, Greenhouse, Lever, …) and return normalised Job entities.
 */
public interface JobBoardAdapter {

    /** The board type this adapter handles, e.g. "ASHBY". */
    String boardType();

    /**
     * Fetch all open jobs from the given careers URL.
     *
     * @param careersUrl the company's board URL / API endpoint
     * @param companyId  the internal company id (set on each returned Job)
     * @return list of partially-hydrated Job entities (no id yet)
     */
    List<Job> fetchJobs(String careersUrl, Long companyId);
}
