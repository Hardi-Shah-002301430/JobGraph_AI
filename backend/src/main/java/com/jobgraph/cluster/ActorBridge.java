package com.jobgraph.cluster;

import akka.actor.typed.ActorRef;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.AskPattern;
import com.jobgraph.exception.AgentTimeoutException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * Utility that wraps Akka's ask pattern so Spring controllers
 * can call agents with a simple one-liner and get a CompletionStage.
 */
@Component
@RequiredArgsConstructor
public class ActorBridge {

    private final ActorSystem<Void> actorSystem;

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Ask an actor and return a CompletionStage of the reply.
     *
     * @param actorRef the target actor
     * @param msgFactory builds the message given the reply-to ActorRef
     * @param <Req>  request message type
     * @param <Res>  response message type
     */
    public <Req, Res> CompletionStage<Res> ask(
            ActorRef<Req> actorRef,
            Function<ActorRef<Res>, Req> msgFactory) {
        return ask(actorRef, msgFactory, DEFAULT_TIMEOUT);
    }

    public <Req, Res> CompletionStage<Res> ask(
            ActorRef<Req> actorRef,
            Function<ActorRef<Res>, Req> msgFactory,
            Duration timeout) {
        return AskPattern.<Req, Res>ask(
                actorRef,
                msgFactory::apply,
                timeout,
                actorSystem.scheduler()
        ).exceptionally(ex -> {
            throw new AgentTimeoutException(
                    "Agent did not respond within " + timeout.toSeconds() + "s", ex);
        });
    }
}
