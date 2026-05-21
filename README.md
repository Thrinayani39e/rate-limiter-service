# Distributed Rate Limiter Microservice

**Live:** [https://rate-limiter-39e.duckdns.org/swagger-ui/index.html](https://rate-limiter-39e.duckdns.org/swagger-ui/index.html)

A production-ready distributed rate limiter built with Java 17, Spring Boot 3, Redis, and PostgreSQL.
Supports two algorithms — **Token Bucket** and **Sliding Window** — with full PostgreSQL audit logging.

---

## Architecture

```
                         Client
                            │
                            │ HTTP
                            ▼
               ┌────────────────────────┐
               │    Spring Boot App      │
               │        :8080            │
               │                         │
               │  RateLimiterController  │
               │  ├─ TokenBucketService  │──► Redis :6379
               │  │                      │    (INCR counters / sorted sets)
               │  └─ SlidingWindowSvc   │
               │                         │
               │  AuditLogRepository     │──► PostgreSQL :5432
               │                         │    (audit_logs table)
               └─────────────────────────┘
```

| Layer        | Technology              | Role                              |
|--------------|-------------------------|-----------------------------------|
| API          | Spring Boot 3 / MVC     | REST endpoints                    |
| Rate Limit   | Redis 7                 | Atomic counters & sorted sets     |
| Persistence  | PostgreSQL 15 + JPA     | Audit log storage                 |
| Container    | Docker Compose          | Service orchestration             |

---

## Algorithms

### Token Bucket
- **Capacity:** 10 tokens per 1-second window  
- **Mechanism:** Redis `INCR` with a 1-second TTL that resets the bucket each second  
- **Key:** `rate:tb:{clientId}`

### Sliding Window
- **Limit:** 20 requests per 60-second window  
- **Mechanism:** Redis sorted set — `ZADD` on every request, `ZREMRANGEBYSCORE` prunes stale entries, `ZCARD` checks the current count  
- **Key:** `rate:sw:{clientId}`

---

## Prerequisites

- Docker & Docker Compose  
- Java 17 + Maven 3.6+ (for the build step)

---

## How to Run

```bash
# 1. Build the fat JAR
mvn clean package

# 2. Start the full stack (app + Redis + PostgreSQL)
docker compose up

# Optional: run in the background
docker compose up -d
```

The API is available at `http://localhost:8080`.

---

## API Reference

### POST /api/rate-limit/check

Check whether a client is within its rate limit.

**Request body:**
```json
{
  "clientId": "user123",
  "algorithm": "sliding-window"
}
```

`algorithm` accepts `token-bucket` or `sliding-window`.

**200 OK — request allowed:**
```json
{
  "clientId": "user123",
  "allowed": true,
  "algorithm": "sliding-window",
  "timestamp": "2024-06-01T12:00:00"
}
```

**429 Too Many Requests — rate limited:**
```json
{
  "clientId": "user123",
  "allowed": false,
  "algorithm": "sliding-window",
  "timestamp": "2024-06-01T12:00:00"
}
```

---

### GET /api/rate-limit/audit/{clientId}

Returns the full PostgreSQL audit trail for a client.

**200 OK:**
```json
[
  {
    "id": 1,
    "clientId": "user123",
    "algorithm": "sliding-window",
    "allowed": true,
    "timestamp": "2024-06-01T12:00:00"
  }
]
```

**404 Not Found** — no records exist for that clientId.

---

### GET /api/rate-limit/stats/{clientId}

Returns aggregated statistics computed from the audit table.

**200 OK:**
```json
{
  "clientId": "user123",
  "totalRequests": 50,
  "blockedCount": 5,
  "allowRate": 90.0
}
```

**404 Not Found** — no records exist for that clientId.

---

## Example curl Commands

```bash
# Check rate limit (sliding window)
curl -X POST http://localhost:8080/api/rate-limit/check \
  -H "Content-Type: application/json" \
  -d '{"clientId":"user123","algorithm":"sliding-window"}'

# Check rate limit (token bucket)
curl -X POST http://localhost:8080/api/rate-limit/check \
  -H "Content-Type: application/json" \
  -d '{"clientId":"user456","algorithm":"token-bucket"}'

# Audit log
curl http://localhost:8080/api/rate-limit/audit/user123

# Stats
curl http://localhost:8080/api/rate-limit/stats/user123
```

Import `postman_collection.json` into Postman for pre-built, clickable requests.

---

## Configuration

All credentials are environment-variable driven — no hardcoded secrets.

| Variable      | Default       | Description              |
|---------------|---------------|--------------------------|
| `DB_HOST`     | `localhost`   | PostgreSQL host          |
| `DB_PORT`     | `5432`        | PostgreSQL port          |
| `DB_NAME`     | `ratelimiter` | Database name            |
| `DB_USER`     | `postgres`    | Database username        |
| `DB_PASSWORD` | `postgres`    | Database password        |
| `REDIS_HOST`  | `localhost`   | Redis host               |
| `REDIS_PORT`  | `6379`        | Redis port               |
