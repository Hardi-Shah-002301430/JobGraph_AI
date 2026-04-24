package com.jobgraph.exception;

public class AgentTimeoutException extends RuntimeException {
    public AgentTimeoutException(String message) {
        super(message);
    }

    public AgentTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
