# 🔄 PriceRadar Multi-Instance Upgrade Plan
# Redis + PostgreSQL Migration Guide

> Upgrade your single-instance PriceRadar to support multiple instances
> behind a load balancer using Redis and PostgreSQL.

---

## Prerequisites

### Install & Run Locally

**Redis:**
```bash
# Docker (recommended)
docker run -d --name redis -p 6379:6379 redis:7-alpine

# Or install natively (Ubuntu)
sudo apt install redis-server
sudo systemctl start redis
```

**PostgreSQL:**
```bash
# Docker (recommended)
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_DB=priceradar \
  -e POSTGRES_USER=priceradar \
  -e POSTGRES_PASSWORD=secret \
  postgres:16-alpine

# Or install natively
sudo apt install postgresql
```

**Or use Docker Compose (best approach — one command starts everything):**
```yaml
# docker-compose.yml — put this in your project root
version: '3.8'
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  postgres:
    image: postgres:16-alpine
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: priceradar
      POSTGRES_USER: priceradar
      POSTGRES_PASSWORD: secret

  # Run two instances of your app to test multi-instance behavior
  app-1:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/priceradar
      SERVER_PORT: 8080
    depends_on:
      - redis
      - postgres

  app-2:
    build: .
    ports:
      - "8081:8080"
    environment:
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/priceradar
      SERVER_PORT: 8080
    depends_on:
      - redis
      - postgres
```

### Add Dependencies to pom.xml

```xml
<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- PostgreSQL + JPA -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

### application.yml

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
  datasource:
    url: jdbc:postgresql://localhost:5432/priceradar
    username: priceradar
    password: secret
  jpa:
    hibernate:
      ddl-auto: update    # auto-creates tables (dev only!)
    show-sql: true
```

---

## Migration Phases

Migrate ONE component at a time. Test after each phase.
Each phase is independent — you can ship after any phase.


═══════════════════════════════════════════════════════════
PHASE 1: Price Cache → Redis                (Day 1)
═══════════════════════════════════════════════════════════

### What Changes

| Before (Single Instance) | After (Multi-Instance) |
|---|---|
| `ConcurrentHashMap<String, CachedPrice>` | Redis key-value with TTL |
| Manual expiry check on `cachedAt` | Redis auto-expires keys |
| `computeIfAbsent()` for atomic check-and-populate | `GET` → miss? → `SET` with TTL |

### Redis Key Design

```
Key pattern:  "price:{product}"
Value:        JSON-serialized List<PriceResult>
TTL:          60 seconds (auto-expires — no manual cleanup!)

Example:
  Key:   "price:iphone"
  Value: [{"supplier":"Amazon","price":799.0}, {"supplier":"Ebay","price":849.0}]
  TTL:   60s
```

### Code to Write

**1. Redis Configuration (serialization setup)**

```java
// RedisConfig.java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

Why: By default, Redis stores Java objects using JDK serialization (ugly binary).
This config makes it store human-readable JSON instead.

**2. Replace PriceCacheService**

```java
@Service
public class PriceCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final PriceAggregatorService aggregator;
    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    public PriceCacheService(RedisTemplate<String, Object> redisTemplate,
                              PriceAggregatorService aggregator) {
        this.redisTemplate = redisTemplate;
        this.aggregator = aggregator;
    }

    public List<PriceResult> search(String product) {
        String key = "price:" + product;

        // Step 1: Try cache
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            // Cache hit — return immediately
            return deserialize(cached);
        }

        // Step 2: Cache miss — fetch from suppliers (parallel!)
        List<PriceResult> results = aggregator.fetchAllPrices(product);

        // Step 3: Store in Redis with TTL
        redisTemplate.opsForValue().set(key, results, CACHE_TTL);

        return results;
    }
}
```

**3. Cache Stats with Redis Atomic Counters**

```java
// Replace AtomicLong with Redis INCR
public void recordCacheHit() {
    redisTemplate.opsForValue().increment("stats:cache:hits");
}

public void recordCacheMiss() {
    redisTemplate.opsForValue().increment("stats:cache:misses");
}

public Map<String, Long> getCacheStats() {
    Long hits = (Long) redisTemplate.opsForValue().get("stats:cache:hits");
    Long misses = (Long) redisTemplate.opsForValue().get("stats:cache:misses");
    return Map.of(
        "hits", hits != null ? hits : 0L,
        "misses", misses != null ? misses : 0L
    );
}
```

### Race Condition Note

You asked about this earlier — yes, there IS a small race window between
GET (miss) and SET. Two instances could both miss and both fetch from suppliers.
This is called "cache stampede." For a price cache, this is usually acceptable —
the worst case is two parallel fetches instead of one.

If you need to prevent it, Redis has `SET key value NX EX 60` (set-if-not-exists
with expiry) which can be used to implement a distributed lock. But for this
project, the simple pattern above is the right choice.

### How to Test

1. Start Redis + your app
2. `curl localhost:8080/api/search?product=iphone` → slow (cache miss)
3. `curl localhost:8080/api/search?product=iphone` → instant (cache hit)
4. Open `redis-cli` and run: `GET price:iphone` → see JSON data
5. Run `TTL price:iphone` → see countdown from 60
6. Wait 60 seconds, search again → slow again (key expired)


═══════════════════════════════════════════════════════════
PHASE 2: Rate Limiter → Redis              (Day 2)
═══════════════════════════════════════════════════════════

### What Changes

| Before | After |
|---|---|
| `ConcurrentHashMap<String, AtomicInteger>` | Redis `INCR` + `EXPIRE` |
| `ScheduledExecutorService` to reset counters | Redis TTL auto-resets |
| Per-instance limit (exploitable) | Global limit across all instances |

### Redis Key Design

```
Key pattern:  "ratelimit:{product}:{minute}"
Value:        integer count
TTL:          60 seconds

Example:
  Key:   "ratelimit:iphone:2026-03-22T14:05"
  Value: 7
  TTL:   60s
```

The trick: include the current minute in the key. When the minute changes,
it's a new key — starts at 0 automatically. Old keys expire via TTL.

### Code to Write

```java
@Service
public class RateLimiterService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final int MAX_REQUESTS_PER_MINUTE = 10;

    public RateLimiterService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAllowed(String product) {
        String minute = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"));
        String key = "ratelimit:" + product + ":" + minute;

        // INCR is atomic in Redis — safe across all instances
        Long count = redisTemplate.opsForValue().increment(key);

        // Set TTL only on first request (when count == 1)
        if (count == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(60));
        }

        return count <= MAX_REQUESTS_PER_MINUTE;
    }
}
```

### Why This Works

Redis `INCR` is atomic — even if 4 instances call it simultaneously on the
same key, Redis processes them one at a time. No race condition, no lost counts.
The ScheduledExecutorService for counter resets is completely eliminated — Redis
TTL handles it automatically.

### How to Test

1. Fire 11 rapid requests for the same product
2. First 10 succeed, 11th gets 429
3. Check Redis: `GET ratelimit:iphone:14:05` → shows "11"
4. Start a SECOND instance on port 8081
5. Fire requests alternating between 8080 and 8081
6. Global limit still enforced at 10 total (not 10 per instance)


═══════════════════════════════════════════════════════════
PHASE 3: Alerts → PostgreSQL               (Day 3-4)
═══════════════════════════════════════════════════════════

### What Changes

| Before | After |
|---|---|
| `ConcurrentHashMap<String, List<PriceAlert>>` | PostgreSQL table |
| `AtomicInteger` for alert IDs | Database auto-increment / UUID |
| Data lost on restart | Data survives restarts |
| Per-instance alerts | Shared across all instances |

### Database Schema

```sql
CREATE TABLE price_alerts (
    id          UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     VARCHAR(100) NOT NULL,
    product     VARCHAR(200) NOT NULL,
    threshold   DECIMAL(10,2) NOT NULL,
    active      BOOLEAN DEFAULT TRUE,
    created_at  TIMESTAMP DEFAULT NOW(),
    triggered_at TIMESTAMP NULL
);

CREATE INDEX idx_alerts_active ON price_alerts (active) WHERE active = TRUE;
CREATE INDEX idx_alerts_user   ON price_alerts (user_id);
```

### Code to Write

**1. JPA Entity**

```java
@Entity
@Table(name = "price_alerts")
public class PriceAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String product;

    @Column(nullable = false)
    private BigDecimal threshold;

    private boolean active = true;

    private Instant createdAt = Instant.now();
    private Instant triggeredAt;

    // constructors, getters, setters
}
```

**2. Spring Data Repository**

```java
public interface PriceAlertRepository extends JpaRepository<PriceAlert, UUID> {

    List<PriceAlert> findByUserIdAndActiveTrue(String userId);

    List<PriceAlert> findByProductAndActiveTrue(String product);

    List<PriceAlert> findByActiveTrue();
}
```

**3. Updated AlertService**

```java
@Service
public class AlertService {

    private final PriceAlertRepository repo;

    public PriceAlert createAlert(String userId, String product, BigDecimal threshold) {
        PriceAlert alert = new PriceAlert();
        alert.setUserId(userId);
        alert.setProduct(product);
        alert.setThreshold(threshold);
        return repo.save(alert);
    }

    public List<PriceAlert> getUserAlerts(String userId) {
        return repo.findByUserIdAndActiveTrue(userId);
    }

    public void deactivateAlert(UUID alertId) {
        repo.findById(alertId).ifPresent(alert -> {
            alert.setActive(false);
            alert.setTriggeredAt(Instant.now());
            repo.save(alert);
        });
    }

    public List<PriceAlert> getAllActiveAlerts() {
        return repo.findByActiveTrue();
    }
}
```

### Why PostgreSQL (Not Redis) for Alerts

- Alerts are **durable** — users expect them to survive server restarts
- Alerts have **relationships** — user has many alerts, need to query by user/product
- Alerts need **rich queries** — "all active alerts for iphone" is a SQL natural
- Write volume is **low** — users create/delete alerts occasionally, not per-second

Redis is great for hot, ephemeral data. PostgreSQL is great for structured,
durable data. Use each where it fits.

### How to Test

1. Create alert via POST `/api/alerts`
2. Stop and restart the application
3. GET `/api/alerts/{userId}` → alert is still there (survived restart!)
4. Start second instance on port 8081
5. Create alert on :8080, query on :8081 → both see the same alerts


═══════════════════════════════════════════════════════════
PHASE 4: Notifications → Redis Pub/Sub     (Day 5-6)
═══════════════════════════════════════════════════════════

### The Problem

```
Instance A: scheduler detects price drop for user1's alert
Instance B: holds user1's SSE connection

How does Instance A tell Instance B to push the notification?
```

### The Solution: Redis Pub/Sub

Redis can act as a message broker. Instance A publishes a message to a
channel. Instance B (and any other instance) subscribes to that channel
and receives the message instantly.

```
Instance A (scheduler)                    Redis                     Instance B (SSE)
    │                                       │                            │
    │ PUBLISH "alerts" {userId, product}    │                            │
    │──────────────────────────────────────▶│                            │
    │                                       │  message to subscribers    │
    │                                       │───────────────────────────▶│
    │                                       │                            │
    │                                       │              push to SSE emitter
    │                                       │                            │
```

### Code to Write

**1. Redis Pub/Sub Configuration**

```java
@Configuration
public class RedisPubSubConfig {

    @Bean
    public ChannelTopic alertTopic() {
        return new ChannelTopic("price-alerts");
    }

    @Bean
    public MessageListenerAdapter messageListener(AlertNotificationListener listener) {
        return new MessageListenerAdapter(listener, "onAlertTriggered");
    }

    @Bean
    public RedisMessageListenerContainer container(
            RedisConnectionFactory factory,
            MessageListenerAdapter adapter,
            ChannelTopic topic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(adapter, topic);
        return container;
    }
}
```

**2. Publisher (in the scheduler)**

```java
@Service
public class AlertPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic topic;

    public void publishAlert(String userId, String product, double currentPrice) {
        Map<String, Object> message = Map.of(
            "userId", userId,
            "product", product,
            "price", currentPrice,
            "timestamp", Instant.now().toString()
        );
        redisTemplate.convertAndSend(topic.getTopic(), message);
    }
}
```

**3. Subscriber (handles SSE push)**

```java
@Service
public class AlertNotificationListener {

    private final NotificationService notificationService;

    // This method is called on EVERY instance that subscribes
    public void onAlertTriggered(Map<String, Object> message) {
        String userId = (String) message.get("userId");
        String product = (String) message.get("product");
        double price = (double) message.get("price");

        // Only pushes if THIS instance holds the user's SSE connection
        notificationService.pushToUser(userId,
            "Price alert! " + product + " dropped to $" + price);
    }
}
```

**4. SSE Emitter Registry (stays in-memory — but now Pub/Sub bridges instances)**

```java
@Service
public class NotificationService {

    // Local to this instance — SSE connections are inherently per-instance
    private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);
        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        return emitter;
    }

    public void pushToUser(String userId, String message) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().data(message));
            } catch (IOException e) {
                emitters.remove(userId);
            }
        }
        // If emitter is null, this instance doesn't hold the connection — that's fine.
        // The instance that DOES hold it will also receive the Pub/Sub message.
    }
}
```

### How It Works End-to-End

1. User1 connects SSE to Instance B → emitter stored in B's local ConcurrentHashMap
2. Scheduler on Instance A detects price drop for User1's alert
3. Instance A publishes to Redis channel "price-alerts"
4. BOTH instances receive the message (they're both subscribed)
5. Instance A checks its emitter map → no emitter for User1 → does nothing
6. Instance B checks its emitter map → finds User1's emitter → pushes notification!

### How to Test

1. Open browser SSE stream to `localhost:8081/api/stream/user1` (Instance B)
2. Create alert on Instance A (port 8080)
3. Wait for scheduler to detect price drop
4. See notification arrive in Instance B's SSE stream
5. Check Redis: `PUBSUB CHANNELS` → shows "price-alerts"


═══════════════════════════════════════════════════════════
PHASE 5: Stats → Redis Counters            (Day 7)
═══════════════════════════════════════════════════════════

### What Changes

Replace all `AtomicLong` counters with Redis `INCR`:

```java
@Service
public class StatsService {

    private final RedisTemplate<String, Object> redisTemplate;

    public void recordRequest() {
        redisTemplate.opsForValue().increment("stats:total-requests");
    }

    public void recordSupplierCall(String supplier) {
        redisTemplate.opsForValue().increment("stats:supplier:" + supplier);
    }

    public Map<String, Object> getStats() {
        return Map.of(
            "totalRequests", getCounter("stats:total-requests"),
            "cacheHits", getCounter("stats:cache:hits"),
            "cacheMisses", getCounter("stats:cache:misses"),
            "activeAlerts", alertRepository.countByActiveTrue()
        );
    }

    private long getCounter(String key) {
        Object val = redisTemplate.opsForValue().get(key);
        return val != null ? Long.parseLong(val.toString()) : 0L;
    }
}
```

Now `/api/stats` returns accurate numbers regardless of which instance serves it.


═══════════════════════════════════════════════════════════
PHASE 6: Verify Multi-Instance Setup       (Day 8)
═══════════════════════════════════════════════════════════

### The Moment of Truth

Start two instances and verify everything works across them:

```bash
# Terminal 1
SERVER_PORT=8080 java -jar priceradar.jar

# Terminal 2
SERVER_PORT=8081 java -jar priceradar.jar
```

### Test Checklist

| Test | How | Expected Result |
|---|---|---|
| Cache sharing | Search on :8080, then :8081 | Second call is instant (cache hit from Redis) |
| Rate limiting | Alternate requests between ports | Global limit of 10, not 10 per instance |
| Alert persistence | Create on :8080, query on :8081 | Both see the same alerts |
| SSE cross-instance | SSE on :8081, trigger on :8080 | Notification arrives via Redis Pub/Sub |
| Stats accuracy | Hit both instances, check /stats | Totals reflect ALL requests |
| Graceful shutdown | Stop :8080, keep using :8081 | No data loss, everything still works |
| Restart recovery | Stop and restart :8080 | Alerts still there, cache still warm |


═══════════════════════════════════════════════════════════
SUMMARY: What Stayed vs What Changed
═══════════════════════════════════════════════════════════

### Still Using JDK Concurrency (within each instance)

| Tool | Where |
|---|---|
| `FixedThreadPool` | Parallel supplier calls (still per-instance) |
| `ScheduledExecutorService` | Background scheduler (runs on each instance) |
| `ConcurrentHashMap` | SSE emitter registry (local connections only) |
| `volatile` | Shutdown flag (per-instance lifecycle) |
| `synchronized` / locks | Any local thread coordination |

### Replaced with External Systems (across instances)

| Was | Now | Why |
|---|---|---|
| `ConcurrentHashMap` (cache) | Redis GET/SET + TTL | Shared cache, auto-expiry |
| `AtomicInteger` (rate limit) | Redis INCR + EXPIRE | Global rate limiting |
| `ConcurrentHashMap` (alerts) | PostgreSQL + JPA | Durable, queryable, survives restarts |
| `AtomicLong` (counters) | Redis INCR | Accurate global stats |
| Direct SSE push | Redis Pub/Sub + local SSE | Cross-instance notifications |
| `AtomicInteger` (ID gen) | UUID / DB sequence | Globally unique, no collisions |

### Key Lesson

Local concurrency tools (ConcurrentHashMap, AtomicInteger, etc.) handle
THREADS within a JVM. External systems (Redis, PostgreSQL) handle
COORDINATION between JVMs. Production systems use BOTH layers together.