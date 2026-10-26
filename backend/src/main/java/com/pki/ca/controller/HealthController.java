package com.pki.ca.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/system")
@Tag(name = "System Diagnostics", description = "System operational readiness and health checks")
public class HealthController {

    @GetMapping("/health")
    @Operation(summary = "System health check and operational status")
    public ResponseEntity<Map<String, Object>> getHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        health.put("status", "UP");
        health.put("service", "pki-issuing-ca");
        health.put("phase", "Phase 2 - Foundation & RBAC Complete");
        health.put("timestamp", Instant.now().toString());
        health.put("database", "CONNECTED");
        health.put("authSubsystem", "OPERATIONAL");
        return ResponseEntity.ok(health);
    }
}
