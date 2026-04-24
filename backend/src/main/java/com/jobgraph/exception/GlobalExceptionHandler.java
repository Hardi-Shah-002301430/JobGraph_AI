package com.jobgraph.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(AgentTimeoutException.class)
    public ResponseEntity<Map<String, Object>> handleTimeout(AgentTimeoutException e) {
        log.warn("Agent timeout: {}", e.getMessage());
        return build(HttpStatus.GATEWAY_TIMEOUT, "AGENT_TIMEOUT", e.getMessage());
    }

    @ExceptionHandler(JobBoardApiException.class)
    public ResponseEntity<Map<String, Object>> handleBoardApi(JobBoardApiException e) {
        log.warn("Job board API error ({}): {}", e.getBoardType(), e.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "JOB_BOARD_ERROR", e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Validation failed");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception e) {
        log.error("Unhandled exception", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                e.getMessage() != null ? e.getMessage() : "Something went wrong");
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String code, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}
