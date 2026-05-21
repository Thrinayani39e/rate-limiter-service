package com.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class SlidingWindowService {

    private static final int MAX_REQUESTS = 20;
    private static final long WINDOW_SECONDS = 60;
    private static final String KEY_PREFIX = "rate:sw:";

    private final StringRedisTemplate redisTemplate;

    public SlidingWindowService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        long now = System.currentTimeMillis();
        long windowStart = now - WINDOW_SECONDS * 1000;

        // Evict entries that have fallen outside the sliding window
        redisTemplate.opsForZSet().removeRangeByScore(key, 0, windowStart);

        Long count = redisTemplate.opsForZSet().zCard(key);

        if (count == null || count < MAX_REQUESTS) {
            // UUID member guarantees uniqueness under concurrent load
            redisTemplate.opsForZSet().add(key, UUID.randomUUID().toString(), now);
            redisTemplate.expire(key, WINDOW_SECONDS, TimeUnit.SECONDS);
            return true;
        }

        return false;
    }
}
