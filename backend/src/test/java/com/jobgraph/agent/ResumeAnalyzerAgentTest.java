package com.jobgraph.agent;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import com.jobgraph.message.AnalysisMessages.*;
import com.jobgraph.model.ResumeProfile;
import com.jobgraph.model.User;
import com.jobgraph.repository.ResumeProfileRepository;
import com.jobgraph.repository.UserRepository;
import com.jobgraph.service.ResumeParserService;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResumeAnalyzerAgentTest {

    private static ActorTestKit testKit;

    @BeforeAll static void setup() { testKit = ActorTestKit.create(); }
    @AfterAll static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void shouldExtractProfileFromResume() {
        ResumeParserService parser = mock(ResumeParserService.class);
        ResumeProfileRepository repo = mock(ResumeProfileRepository.class);
        UserRepository userRepo = mock(UserRepository.class);

        User user = User.builder().id(1L).email("test@example.com").build();
        when(userRepo.findById(eq(1L))).thenReturn(Optional.of(user));

        ResumeProfile parsed = ResumeProfile.builder()
                .fullName("Hardi Shah")
                .email("hardi@example.com")
                .skills(List.of("Java", "Spring", "Akka"))
                .experienceYears(3)
                .educationLevel("BACHELORS")
                .preferredRoles(List.of("Backend Engineer"))
                .rawText("resume text")
                .build();

        when(parser.parse(any())).thenReturn(parsed);
        when(repo.save(any(ResumeProfile.class))).thenAnswer(inv -> {
            ResumeProfile r = inv.getArgument(0);
            r.setId(42L);
            return r;
        });

        var ref = testKit.spawn(ResumeAnalyzerAgent.create(parser, repo, userRepo));
        TestProbe<AnalysisResult> probe = testKit.createTestProbe();

        // First arg is now userId, not resumeId
        ref.tell(new AnalyzeResume(1L, "My resume text", probe.getRef()));

        AnalysisResult result = probe.expectMessageClass(AnalysisResult.class, Duration.ofSeconds(2));
        assertTrue(result.isSuccess());
        assertEquals(42L, result.getResumeId());
        assertEquals("Hardi Shah", result.getFullName());
        assertEquals(3, result.getSkills().size());

        // Verify the agent attached the user to the profile before saving.
        verify(repo).save(argThat(p -> p.getUser() != null && p.getUser().getId().equals(1L)));
    }

    @Test
    void shouldFailWhenUserDoesNotExist() {
        ResumeParserService parser = mock(ResumeParserService.class);
        ResumeProfileRepository repo = mock(ResumeProfileRepository.class);
        UserRepository userRepo = mock(UserRepository.class);
        when(userRepo.findById(any())).thenReturn(Optional.empty());

        var ref = testKit.spawn(ResumeAnalyzerAgent.create(parser, repo, userRepo));
        TestProbe<AnalysisResult> probe = testKit.createTestProbe();

        ref.tell(new AnalyzeResume(999L, "some text", probe.getRef()));

        AnalysisResult result = probe.expectMessageClass(AnalysisResult.class, Duration.ofSeconds(2));
        assertFalse(result.isSuccess());
        verify(parser, never()).parse(any());   // LLM call was skipped — fail fast
        verify(repo, never()).save(any(ResumeProfile.class));
    }
}