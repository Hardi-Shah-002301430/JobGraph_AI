package com.jobgraph.config;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AkkaConfig {

    @Value("${jobgraph.akka.config-file:akka-common.conf}")
    private String akkaConfigFile;

    private ActorSystem<Void> system;

    @Bean
    public ActorSystem<Void> actorSystem() {
        Config akkaConf = ConfigFactory.load(akkaConfigFile);
        system = ActorSystem.create(Behaviors.empty(), "JobGraphCluster", akkaConf);
        return system;
    }

    @PreDestroy
    public void shutdown() {
        if (system != null) {
            system.terminate();
        }
    }
}
