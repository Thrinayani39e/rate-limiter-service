package com.ratelimiter.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class TokenBucketService {

    private static final int CAPACITY = 10;
    private static final String KEY_PREFIX = "rate:tb:";

    private final StringRedisTemplate redisTemplate;

    public TokenBucketService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String clientId) {
        String key = KEY_PREFIX + clientId;
        Long count = redisTemplate.opsForValue().increment(key);

        if (count == null) {
            return false;
        }

        // First request in this window — arm the 1-second TTL (1 token/sec refill via reset)
        if (count == 1) {
            redisTemplate.expire(key, 1, TimeUnit.SECONDS);
        }

        return count <= CAPACITY;
    }
}
