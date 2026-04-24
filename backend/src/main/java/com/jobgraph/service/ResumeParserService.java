package com.jobgraph.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.jobgraph.model.ResumeProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResumeParserService {

    private final LlmService llmService;

    private static final Set<String> VALID_EDUCATION_LEVELS =
            Set.of("HIGH_SCHOOL", "ASSOCIATE", "BACHELORS", "MASTERS", "PHD");

    private static final int MAX_SKILLS = 30;
    private static final int MAX_ROLES = 5;

    /**
     * The system prompt is the single biggest lever for output quality.
     * Changes here directly affect match scores downstream, so edit with care.
     * Structured in sections: ROLE / TASK / RULES / EXAMPLE / OUTPUT.
     */
    private static final String SYSTEM_PROMPT = """
        ROLE
        You are a technical resume parser for a job-matching platform.
        Your output feeds a matching algorithm, so precision matters more than completeness.

        TASK
        Extract structured data from the resume text that follows.
        Return ONLY a single JSON object — no prose, no markdown, no code fences.

        OUTPUT SCHEMA
        {
          "fullName": string,
          "email": string,
          "phone": string or null,
          "summary": string (1–2 sentences, your own words, 3rd person),
          "skills": [string, ...],
          "experienceYears": integer,
          "educationLevel": "HIGH_SCHOOL" | "ASSOCIATE" | "BACHELORS" | "MASTERS" | "PHD",
          "preferredRoles": [string, ...]
        }

        FIELD RULES

        experienceYears:
          - Sum full-time professional experience in years.
          - Count internships at 0.5× weight (6 months internship = 0.25 years).
          - Do NOT count time spent as a full-time student.
          - If ranges say "Present", assume the current date.
          - Round DOWN to the nearest integer. If total is < 1, return 0.

        skills:
          - Extract from BOTH the dedicated Skills section AND any technologies
            mentioned in project/job descriptions (e.g. "deployed on Kubernetes"
            means add "Kubernetes").
          - Use canonical names: "Kubernetes" (not "k8s"), "PostgreSQL" (not "Postgres"),
            "JavaScript" (not "JS"), "TypeScript" (not "TS"), "CI/CD" (not "CICD").
          - Deduplicate. Keep at most 30. Prefer technical skills over soft skills.

        educationLevel:
          - Return the highest degree ALREADY COMPLETED (graduated).
          - If a higher degree is currently in progress, DO NOT use it — use the
            last completed one, and mention the in-progress degree in `summary`.

        preferredRoles:
          - 3–5 specific, searchable job titles that match this candidate's
            concrete experience.
          - GOOD: "Site Reliability Engineer", "Platform Engineer",
            "Developer Experience Engineer", "Backend Engineer (Distributed Systems)".
          - BAD (too generic): "Software Engineer", "Developer", "Engineer".
          - Bias toward roles the candidate could pass a screen for today,
            not aspirational ones.

        EXAMPLE

        Input resume excerpt:
          "Jane Doe — jane@example.com
           MS Computer Science, MIT, 2021 (completed)
           Senior ML Engineer at Google, 2021–Present. Built training pipelines
           on Kubernetes with PyTorch. Shipped a feature store used by 30 teams.
           Intern at Meta, Summer 2020."

        Expected output:
          {
            "fullName": "Jane Doe",
            "email": "jane@example.com",
            "phone": null,
            "summary": "Senior ML engineer with 3+ years at Google building training infrastructure and feature platforms for large-scale ML.",
            "skills": ["Python", "PyTorch", "Kubernetes", "Machine Learning", "Distributed Systems"],
            "experienceYears": 3,
            "educationLevel": "MASTERS",
            "preferredRoles": ["Machine Learning Engineer", "ML Platform Engineer", "ML Infrastructure Engineer", "Senior Software Engineer (ML)"]
          }

        Now parse the resume below. Return JSON only.
        """;

    public ResumeProfile parse(String rawText) {
        log.info("Parsing resume via LLM ({} chars)", rawText.length());
        JsonNode json = llmService.chatJson(SYSTEM_PROMPT, rawText);

        String fullName = textOrNull(json, "fullName");
        String email = textOrNull(json, "email");
        String phone = textOrNull(json, "phone");
        String summary = textOrNull(json, "summary");

        List<String> skills = extractStringArray(json, "skills", MAX_SKILLS);
        List<String> roles = extractStringArray(json, "preferredRoles", MAX_ROLES);

        int years = json.path("experienceYears").asInt(0);
        if (years < 0 || years > 60) {
            log.warn("Implausible experienceYears={} — clamping to 0", years);
            years = 0;
        }

        String eduRaw = json.path("educationLevel").asText("").toUpperCase().trim();
        String education;
        if (VALID_EDUCATION_LEVELS.contains(eduRaw)) {
            education = eduRaw;
        } else {
            log.warn("LLM returned invalid educationLevel='{}' — defaulting to BACHELORS", eduRaw);
            education = "BACHELORS";
        }

        // Required-field sanity checks — log loudly so bad parses surface in prod
        if (fullName == null || fullName.isBlank()) {
            log.warn("LLM did not extract fullName from resume");
        }
        if (email == null || email.isBlank()) {
            log.warn("LLM did not extract email from resume");
        }
        if (skills.isEmpty()) {
            log.warn("LLM returned zero skills — prompt may need tuning");
        }
        if (roles.isEmpty()) {
            log.warn("LLM returned zero preferredRoles — downstream matching will fail");
        }

        log.info("Parsed: name={}, years={}, education={}, {} skills, {} roles",
                fullName, years, education, skills.size(), roles.size());

        return ResumeProfile.builder()
                .fullName(fullName)
                .email(email)
                .phone(phone)
                .summary(summary)
                .rawText(rawText)
                .skills(skills)
                .experienceYears(years)
                .educationLevel(education)
                .preferredRoles(roles)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ──────────────────────────────────────────────────────────

    private static String textOrNull(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return s.isBlank() ? null : s.trim();
    }

    private static List<String> extractStringArray(JsonNode node, String field, int max) {
        List<String> out = new ArrayList<>();
        JsonNode arr = node.path(field);
        if (!arr.isArray()) return out;

        for (JsonNode item : arr) {
            String s = item.asText("").trim();
            if (!s.isBlank() && !out.contains(s)) {
                out.add(s);
                if (out.size() >= max) break;
            }
        }
        return out;
    }
}