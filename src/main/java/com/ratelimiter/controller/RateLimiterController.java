package com.ratelimiter.controller;

import com.ratelimiter.entity.AuditLog;
import com.ratelimiter.model.RateLimitRequest;
import com.ratelimiter.repository.AuditLogRepository;
import com.ratelimiter.service.SlidingWindowService;
import com.ratelimiter.service.TokenBucketService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rate-limit")
public class RateLimiterController {

    private final TokenBucketService tokenBucketService;
    private final SlidingWindowService slidingWindowService;
    private final AuditLogRepository auditLogRepository;

    public RateLimiterController(
            TokenBucketService tokenBucketService,
            SlidingWindowService slidingWindowService,
            AuditLogRepository auditLogRepository) {
        this.tokenBucketService = tokenBucketService;
        this.slidingWindowService = slidingWindowService;
        this.auditLogRepository = auditLogRepository;
    }

    @PostMapping("/check")
    public ResponseEntity<Map<String, Object>> check(@RequestBody RateLimitRequest request) {
        String algorithm = request.getAlgorithm();
        boolean allowed;

        if ("token-bucket".equals(algorithm)) {
            allowed = tokenBucketService.isAllowed(request.getClientId());
        } else if ("sliding-window".equals(algorithm)) {
            allowed = slidingWindowService.isAllowed(request.getClientId());
        } else {
            Map<String, Object> error = new HashMap<>();
            error.put("error", "Unknown algorithm. Use 'token-bucket' or 'sliding-window'.");
            return ResponseEntity.badRequest().body(error);
        }

        LocalDateTime now = LocalDateTime.now();

        auditLogRepository.save(AuditLog.builder()
                .clientId(request.getClientId())
                .algorithm(algorithm)
                .allowed(allowed)
                .timestamp(now)
                .build());

        Map<String, Object> response = new HashMap<>();
        response.put("clientId", request.getClientId());
        response.put("allowed", allowed);
        response.put("algorithm", algorithm);
        response.put("timestamp", now);

        HttpStatus status = allowed ? HttpStatus.OK : HttpStatus.TOO_MANY_REQUESTS;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/audit/{clientId}")
    public ResponseEntity<List<AuditLog>> getAudit(@PathVariable String clientId) {
        List<AuditLog> logs = auditLogRepository.findByClientId(clientId);
        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/stats/{clientId}")
    public ResponseEntity<Map<String, Object>> getStats(@PathVariable String clientId) {
        List<AuditLog> logs = auditLogRepository.findByClientId(clientId);
        if (logs.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        long total = logs.size();
        long blocked = logs.stream().filter(l -> !l.isAllowed()).count();
        double allowRate = Math.round(((double) (total - blocked) / total) * 10000.0) / 100.0;

        Map<String, Object> stats = new HashMap<>();
        stats.put("clientId", clientId);
        stats.put("totalRequests", total);
        stats.put("blockedCount", blocked);
        stats.put("allowRate", allowRate);

        return ResponseEntity.ok(stats);
    }
}
