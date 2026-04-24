package com.jobgraph.config;

import com.jobgraph.websocket.JobUpdateHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final JobUpdateHandler jobUpdateHandler;

    public WebSocketConfig(JobUpdateHandler jobUpdateHandler) {
        this.jobUpdateHandler = jobUpdateHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(jobUpdateHandler, "/ws/jobs")
                .setAllowedOrigins("*");
    }
}
