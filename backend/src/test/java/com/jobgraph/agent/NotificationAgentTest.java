package com.jobgraph.agent;

import akka.actor.testkit.typed.javadsl.ActorTestKit;
import akka.actor.testkit.typed.javadsl.TestProbe;
import com.jobgraph.message.NotificationMessages.*;
import com.jobgraph.service.SlackService;
import org.junit.jupiter.api.*;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationAgentTest {

    private static ActorTestKit testKit;

    @BeforeAll static void setup() { testKit = ActorTestKit.create(); }
    @AfterAll static void teardown() { testKit.shutdownTestKit(); }

    @Test
    void shouldSendSlackAndReportSuccess() {
        SlackService slack = mock(SlackService.class);
        when(slack.send("hello")).thenReturn(true);

        var ref = testKit.spawn(NotificationAgent.create(slack));
        TestProbe<SlackSent> probe = testKit.createTestProbe();

        ref.tell(new SendSlack("#channel", "hello", probe.getRef()));

        SlackSent result = probe.expectMessageClass(SlackSent.class, Duration.ofSeconds(2));
        assertTrue(result.isSuccess());
    }
}
