package com.jobgraph.agent;

import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.*;
import com.jobgraph.message.NotificationMessages.*;
import com.jobgraph.service.SlackService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class NotificationAgent extends AbstractBehavior<SendSlack> {

    private final SlackService slackService;

    private NotificationAgent(ActorContext<SendSlack> ctx, SlackService slackService) {
        super(ctx);
        this.slackService = slackService;
        log.info("NotificationAgent started");
    }

    public static Behavior<SendSlack> create(SlackService slackService) {
        return Behaviors.setup(ctx -> new NotificationAgent(ctx, slackService));
    }

    @Override
    public Receive<SendSlack> createReceive() {
        return newReceiveBuilder()
                .onMessage(SendSlack.class, this::onSend)
                .build();
    }

    private Behavior<SendSlack> onSend(SendSlack cmd) {
        boolean success = slackService.send(cmd.getText());
        log.info("Slack notification sent={} to channel={}", success, cmd.getChannel());
        cmd.getReplyTo().tell(new SlackSent(success, success ? null : "Webhook call failed"));
        return this;
    }
}
