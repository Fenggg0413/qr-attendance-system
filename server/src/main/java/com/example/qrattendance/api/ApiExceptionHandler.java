package com.example.qrattendance.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException err) {
    String message = err.getReason() == null || err.getReason().isBlank() ? err.getStatusCode().toString() : err.getReason();
    return ResponseEntity.status(err.getStatusCode()).body(Map.of("message", message, "error", message));
  }
}
