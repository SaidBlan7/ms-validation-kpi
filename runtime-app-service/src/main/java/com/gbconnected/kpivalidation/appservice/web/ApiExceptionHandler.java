package com.gbconnected.kpivalidation.appservice.web;

import com.gbconnected.kpivalidation.application.AuthorizationException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(SecurityException.class)
    ResponseEntity<Map<String,String>> security(SecurityException e) {
        LOG.warn("event=kpi_validation_request_rejected correlationId={} status=401 reason={}", correlationId(), safe(e.getMessage()));
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", safe(e.getMessage())));
    }

    @ExceptionHandler(AuthorizationException.class)
    ResponseEntity<Map<String,String>> forbidden(AuthorizationException e) {
        LOG.warn("event=kpi_validation_request_rejected correlationId={} status=403 reason={}", correlationId(), safe(e.getMessage()));
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", safe(e.getMessage())));
    }

    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentNotValidException.class})
    ResponseEntity<Map<String,String>> bad(Exception e) {
        LOG.warn("event=kpi_validation_request_rejected correlationId={} status=400 reason={}", correlationId(), safe(e.getMessage()));
        return ResponseEntity.badRequest().body(Map.of("error", safe(e.getMessage())));
    }

    private static String correlationId() {
        String value = MDC.get("correlationId");
        return value == null ? "" : value;
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "Request rejected" : value;
    }
}
