# 🚀 Project: PriceRadar — Real-Time Price Aggregator & Alert System

> A Spring Boot application that fetches prices from multiple supplier APIs in parallel,
> caches results with ConcurrentHashMap, and sends real-time alerts via SSE (Server-Sent Events)
> when prices drop below a user's threshold.

---

## What You'll Build

PriceRadar is a price monitoring service. Users can:
1. **Search for a product** → app calls 3-5 simulated supplier APIs **in parallel**, returns the cheapest price
2. **Set a price alert** → "Notify me when iPhone drops below $800"
3. **Receive real-time notifications** → background scheduler checks prices periodically; when a threshold is hit, the user gets a **live push notification** via Server-Sent Events (SSE)

---

## Why This Project Is Perfect For You

Every concurrency concept you learned gets used in a real, practical way:

| Concept You Learned | How It's Used in PriceRadar |
|---|---|
| `ExecutorService` + `FixedThreadPool` | Parallel API calls to multiple suppliers |
| `Future` / `CompletableFuture` | Collecting and combining parallel results |
| `ConcurrentHashMap` | Thread-safe price cache + alert registry |
| `ScheduledExecutorService` | Background price-check scheduler |
| `AtomicInteger` | Request counters, rate limiting |
| `ReentrantLock` + `Condition` | Alert subscription management |
| `volatile` | Graceful shutdown flag for background tasks |
| SSE (Server-Sent Events) | Real-time push notifications to users |

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                    PriceRadar API                        │
│                                                         │
│  ┌──────────────┐    ┌──────────────────────────────┐   │
│  │  /api/search  │───▶│   PriceAggregatorService     │   │
│  │  (REST)       │    │   • FixedThreadPool(5)       │   │
│  └──────────────┘    │   • Submit to 5 suppliers     │   │
│                       │   • Collect via Future.get()  │   │
│                       │   • Cache in ConcurrentHashMap│   │
│                       └──────────────────────────────┘   │
│                                                         │
│  ┌──────────────┐    ┌──────────────────────────────┐   │
│  │  /api/alerts  │───▶│   AlertService               │   │
│  │  (REST)       │    │   • ConcurrentHashMap of      │   │
│  └──────────────┘    │     user alerts               │   │
│                       │   • AtomicInteger alert count │   │
│  ┌──────────────┐    └──────────────────────────────┘   │
│  │  /api/stream  │                                      │
│  │  (SSE)        │◀── Push notifications when price     │
│  └──────────────┘    drops below threshold              │
│                                                         │
│  ┌──────────────────────────────────────────────────┐   │
│  │   PriceCheckScheduler                             │   │
│  │   • ScheduledExecutorService                      │   │
│  │   • Runs every 30 seconds                         │   │
│  │   • Checks prices against all active alerts       │   │
│  │   • Triggers SSE notifications on match           │   │
│  │   • volatile shutdown flag for graceful stop      │   │
│  └──────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│  Simulated Supplier APIs (no real HTTP — just services)  │
│                                                          │
│  AmazonSupplier   │ EbaySupplier    │ WalmartSupplier    │
│  BestBuySupplier  │ NeweggSupplier                       │
│  (Each has random delay 200-2000ms + random price)       │
└──────────────────────────────────────────────────────────┘
```

---

## 2-Week Build Plan

### WEEK 1: Core Concurrency Features

---

#### Day 1-2: Project Setup + Supplier Simulation

**Goal:** Spring Boot app with simulated supplier services that return random prices with random delays.

**What to build:**
- Spring Boot project (Spring Web dependency only — no database needed)
- `Supplier` interface with a single method: `PriceResult getPrice(String productName)`
- `PriceResult` record: `(String supplierName, String product, double price, long responseTimeMs)`
- 5 implementations (AmazonSupplier, EbaySupplier, etc.) — each sleeps 200-2000ms randomly, returns a random price within a range
- A simple `GET /api/health` endpoint to verify the app runs

**Concurrency concepts:** None yet — this is pure Spring Boot scaffolding.

**Files to create:**
```
src/main/java/com/priceradar/
├── PriceRadarApplication.java
├── model/
│   └── PriceResult.java              (record)
├── supplier/
│   ├── Supplier.java                 (interface)
│   ├── AmazonSupplier.java           (simulated)
│   ├── EbaySupplier.java
│   ├── WalmartSupplier.java
│   ├── BestBuySupplier.java
│   └── NeweggSupplier.java
└── controller/
    └── HealthController.java
```

**Self-check:** Can you call each supplier from a test and see it return a price after a delay?

---

#### Day 3-4: Parallel Price Aggregation (⭐ Core Concurrency)

**Goal:** Call all 5 suppliers in parallel, collect results, return the cheapest.

**What to build:**
- `PriceAggregatorService` with a `FixedThreadPool` of 5 threads
- Method: `List<PriceResult> fetchAllPrices(String product)` — submits all 5 supplier calls as `Callable<PriceResult>`, collects `Future<PriceResult>` list, calls `.get()` with a 3-second timeout on each
- Method: `PriceResult findCheapest(String product)` — calls fetchAllPrices, returns the minimum
- `GET /api/search?product=iphone` endpoint that returns all prices + cheapest
- **Measure and log total response time** — it should be ~2 seconds (slowest supplier), NOT ~5-10 seconds (sum of all)

**Concurrency concepts used:**
- `ExecutorService` / `Executors.newFixedThreadPool()`
- `Callable<T>` and `Future<T>`
- `Future.get(timeout, TimeUnit)` — handle `TimeoutException` for slow suppliers
- Parallel execution proving time savings

**Key decisions to think through:**
- What happens if one supplier times out? Do you skip it or fail the whole request?
- Should the thread pool be created per request or shared across all requests? (Hint: shared — Spring beans are singletons)
- How do you shut down the pool when the app stops? (Hint: `@PreDestroy`)

**Self-check:** Hit the endpoint. Does it return 5 results in ~2 seconds? What if you change the pool to size 1 — how does the response time change?

---

#### Day 5-6: Price Cache with ConcurrentHashMap (⭐ Core Concurrency)

**Goal:** Cache price results so repeated searches don't hit suppliers every time.

**What to build:**
- `PriceCacheService` backed by `ConcurrentHashMap<String, CachedPrice>`
- `CachedPrice` record: `(List<PriceResult> prices, Instant cachedAt)`
- Use `computeIfAbsent()` to atomically check-and-populate the cache
- Cache entries expire after 60 seconds — if `cachedAt` is older than 60s, refetch
- `AtomicLong` counters for cache hits vs cache misses (expose via `GET /api/cache/stats`)
- `GET /api/search?product=iphone` now checks cache first

**Concurrency concepts used:**
- `ConcurrentHashMap` — thread-safe cache
- `computeIfAbsent()` — atomic read-modify-write
- `compute()` — for cache refresh on expiry
- `AtomicLong` — lock-free hit/miss counters

**Key decisions to think through:**
- Why is `computeIfAbsent` better than `if (map.get(key) == null) { map.put(...) }`?
- What happens if 10 requests for "iphone" arrive simultaneously with an empty cache? Does `computeIfAbsent` call the supplier 10 times or 1 time?
- How do you handle cache expiry atomically?

**Self-check:** Hit the same endpoint twice quickly. First call ~2s, second call ~instant. Cache stats show 1 miss, 1 hit.

---

#### Day 7: Request Rate Limiter (⭐ Concurrency Bonus)

**Goal:** Limit each product to max 10 searches per minute to protect suppliers.

**What to build:**
- `RateLimiterService` using `ConcurrentHashMap<String, AtomicInteger>`
- Each product key maps to a request count for the current minute
- `ScheduledExecutorService` resets all counters every 60 seconds
- If limit exceeded, return HTTP 429 (Too Many Requests)
- Implement as a Spring `HandlerInterceptor` or `@Aspect`

**Concurrency concepts used:**
- `ConcurrentHashMap` + `AtomicInteger` — per-key atomic counters
- `computeIfAbsent()` — lazy initialization of counters
- `ScheduledExecutorService` — periodic counter reset

**Self-check:** Send 11 rapid requests for the same product. First 10 succeed, 11th gets 429.

---

### WEEK 2: Real-Time Features + Production Patterns

---

#### Day 8-9: Price Alert System (⭐ Core Concurrency)

**Goal:** Users can set "notify me when price drops below X" alerts.

**What to build:**
- `AlertService` managing alerts in `ConcurrentHashMap<String, List<PriceAlert>>`
- `PriceAlert` record: `(String alertId, String userId, String product, double threshold, Instant createdAt)`
- `POST /api/alerts` — create a new alert
- `GET /api/alerts/{userId}` — list user's active alerts
- `DELETE /api/alerts/{alertId}` — remove an alert
- Use `ConcurrentHashMap.compute()` for thread-safe list modifications
- `AtomicInteger` to generate unique alert IDs

**Concurrency concepts used:**
- `ConcurrentHashMap.compute()` — atomic list manipulation
- `AtomicInteger.incrementAndGet()` — thread-safe ID generation
- Thread-safe collection management patterns

**Key decisions to think through:**
- The value is `List<PriceAlert>` — is `ArrayList` safe inside `compute()`? (Yes — `compute()` holds the bucket lock)
- What about reading the list outside `compute()`? (Hint: consider `CopyOnWriteArrayList`)

---

#### Day 10-11: Background Scheduler + SSE Notifications (⭐ Core Concurrency)

**Goal:** Background job checks prices every 30 seconds; pushes live notifications via SSE.

**What to build:**
- `PriceCheckScheduler` using `ScheduledExecutorService` (or Spring's `@Scheduled`)
    - Every 30 seconds: iterate all alerts, fetch current prices, check thresholds
    - Uses the `PriceAggregatorService` for parallel fetches
    - `volatile boolean running` flag for graceful shutdown
- `NotificationService` using Spring's `SseEmitter`
    - `GET /api/stream/{userId}` — SSE endpoint, returns `SseEmitter`
    - Store emitters in `ConcurrentHashMap<String, SseEmitter>`
    - When a price alert triggers, push event to the user's emitter
    - Handle disconnections (remove dead emitters)

**Concurrency concepts used:**
- `ScheduledExecutorService` / `@Scheduled` — periodic background work
- `volatile` — shutdown flag
- `ConcurrentHashMap` — SSE emitter registry (multiple users connecting/disconnecting concurrently)
- Thread interaction — scheduler thread pushes to SSE emitters owned by HTTP threads

**Self-check:**
1. Open browser to `http://localhost:8080/api/stream/user1` (SSE stream)
2. Create an alert: POST with threshold $50
3. Wait for scheduler to run
4. See notification appear in the browser stream in real-time

---

#### Day 12-13: Monitoring Dashboard + Graceful Shutdown

**Goal:** Expose operational metrics and ensure clean shutdown.

**What to build:**
- `GET /api/stats` endpoint returning:
    - Active thread pool sizes and queue depths
    - Cache hit/miss ratios (from your `AtomicLong` counters)
    - Active alerts count
    - Active SSE connections
    - Total requests served (another `AtomicLong`)
    - Rate limiter status per product
- Graceful shutdown in `@PreDestroy`:
    - Set `volatile` shutdown flag
    - Shut down all pools: aggregator pool, scheduler pool, rate limiter pool
    - Use the shutdown → awaitTermination → shutdownNow pattern for each
    - Close all SSE emitters
    - Log shutdown progress

**Concurrency concepts used:**
- `AtomicLong` — all counters
- `volatile` — shutdown coordination
- Graceful shutdown pattern across multiple pools
- Thread pool introspection (`ThreadPoolExecutor.getActiveCount()`, `getQueue().size()`)

---

#### Day 14: Load Test + Retrospective

**Goal:** Prove your concurrency code works under pressure.

**What to do:**
- Write a simple load test (or use `curl` in a bash loop) that sends 100 concurrent requests
- Observe:
    - Response times stay consistent (parallelism working)
    - Cache hit rate climbs (caching working)
    - Rate limiter kicks in at threshold (rate limiting working)
    - No exceptions or data corruption in logs (thread safety working)
    - Stats endpoint shows accurate counts (atomics working)
- Try deliberately breaking things:
    - Set pool size to 1 — watch response times degrade
    - Remove `ConcurrentHashMap`, use `HashMap` — watch random failures under load
    - Remove `AtomicLong`, use plain `long` — watch counter drift

---

## API Summary

| Method | Endpoint | Description |
|---|---|---|
| GET | `/api/health` | Health check |
| GET | `/api/search?product=iphone` | Parallel price search across all suppliers |
| GET | `/api/cache/stats` | Cache hit/miss ratios |
| POST | `/api/alerts` | Create a price alert |
| GET | `/api/alerts/{userId}` | List user's alerts |
| DELETE | `/api/alerts/{alertId}` | Remove an alert |
| GET | `/api/stream/{userId}` | SSE stream for real-time notifications |
| GET | `/api/stats` | System-wide concurrency metrics |

---

## Tech Stack

- **Java 21** (for virtual threads bonus, records, modern features)
- **Spring Boot 3.x** (Spring Web only — no database needed)
- **No external dependencies** — everything uses JDK concurrency utilities
- **No database** — all state is in-memory (ConcurrentHashMap). This keeps focus on concurrency, not persistence.

---

## Bonus Challenges (After Week 2)

If you finish early or want to push further:

1. **CompletableFuture Refactor** — Replace `Future.get()` in PriceAggregatorService with `CompletableFuture.supplyAsync()` chains. Use `allOf()` to wait for all suppliers, `thenCombine()` to merge results.

2. **Virtual Threads** — Add a toggle: `GET /api/search?product=iphone&useVirtualThreads=true`. Compare response times under heavy load between platform threads and virtual threads.

3. **WebSocket Upgrade** — Replace SSE with WebSocket for bidirectional communication. Users can subscribe/unsubscribe to products in real-time.

4. **Persistent Alerts** — Add H2 database + Spring Data JPA. Alerts survive restarts. ConcurrentHashMap becomes a write-through cache.

5. **Distributed Rate Limiting** — Replace in-memory AtomicInteger with Redis-based counters using `INCR` + `EXPIRE`. See how the concurrency model changes.

---

## Concept Map: What You Learned → Where It's Used

```
Day 1 (ExecutorService)
  └──▶ PriceAggregatorService (FixedThreadPool for parallel supplier calls)
  └──▶ PriceCheckScheduler (ScheduledExecutorService for background checks)

Day 1 (Future / submit)
  └──▶ PriceAggregatorService (collect results from parallel calls)

Day 1 (Shutdown pattern)
  └──▶ @PreDestroy graceful shutdown of all pools

synchronized / volatile / AtomicInteger
  └──▶ AtomicLong for cache stats, request counters, alert IDs
  └──▶ volatile for scheduler shutdown flag

ConcurrentHashMap
  └──▶ PriceCacheService (computeIfAbsent for cache)
  └──▶ AlertService (compute for alert list management)
  └──▶ RateLimiterService (computeIfAbsent + AtomicInteger)
  └──▶ NotificationService (SSE emitter registry)
```