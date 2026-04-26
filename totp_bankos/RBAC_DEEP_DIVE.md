# RBAC Deep Dive — BankOS TOTP Project

> **Goal**: Understand every layer of the system — from database tables to runtime request filtering —
> so you can trace exactly why a user can or cannot call a given API endpoint.

---

## Table of Contents

1. [What is RBAC and Why This Design](#1-what-is-rbac-and-why-this-design)
2. [Data Model — All 14 Tables](#2-data-model--all-14-tables)
3. [The Permission Matrix](#3-the-permission-matrix)
4. [Setup Flow — Step by Step](#4-setup-flow--step-by-step)
5. [Two-Phase Authentication](#5-two-phase-authentication)
6. [Runtime Security Pipeline](#6-runtime-security-pipeline)
7. [Deep Dive: Each Security Layer](#7-deep-dive-each-security-layer)
8. [The Key SQL Query](#8-the-key-sql-query)
9. [Redis Caching Layer](#9-redis-caching-layer)
10. [Audit Logging](#10-audit-logging)
11. [Edge Cases and Rules](#11-edge-cases-and-rules)
12. [Mental Model: The Big Picture](#12-mental-model-the-big-picture)

---

## 1. What is RBAC and Why This Design

**RBAC = Role-Based Access Control.**  
Instead of assigning permissions directly to users, you assign them to *roles* (called **Groups** here),
then assign users to those roles. This way, changing what 100 users can do means updating one group — not 100 records.

### Why this system is more complex than typical RBAC

Most RBAC systems: `User → Role → Permissions`

This system: `User → Wallet → Group (within wallet) → Permissions`

The extra layer (Wallet) exists because:
- One user can have access to **multiple wallets** (e.g., both `Company A` and `Company B` wallets)
- That user might be a **Maker** in wallet 1 but a **Checker** in wallet 2
- Permissions are therefore **wallet-scoped** — they only apply within an active wallet session

```
User "john"
  ├── Wallet 1 (Company A)  ──► Group: Maker  ──► Can: CREATE_REQUEST
  └── Wallet 2 (Company B)  ──► Group: Checker ──► Can: APPROVE, LIST, READ_DETAIL
```

---

## 2. Data Model — All 14 Tables

### Entity Relationship (ASCII)

```
cif (1)
 └──< account_wallets (many wallets per CIF)
       │
       ├──< wallet_users >── users
       │         (many-to-many: a user can access many wallets)
       │
       ├──< groups (wallet-scoped groups, e.g. "Maker" in Wallet-1)
       │     └──< group_permissions >── permissions
       │                (a group has many permissions)
       │
       ├──< wallet_user_groups >── groups
       │         (which group a user belongs to IN THIS wallet)
       │         (UNIQUE: one group per user per wallet)
       │
       ├──< transfer_requests
       ├──< cashin_requests
       └──< active_wallet_sessions (one per user, the current active wallet)

permissions = features × functions (25 total)
features: CASHIN, CASHOUT, TRANSFER, LINK_BANK, UNLINK_BANK
functions: LIST, READ_DETAIL, CREATE_REQUEST, APPROVE, EXPORT_EXCEL

audit_log (append-only, records every permission check)
```

### Key Tables Explained

#### `cif` — Customer
```sql
id, code (unique), name, is_active, created_at, updated_at
```
The top-level customer. All wallets belong to a CIF.

#### `account_wallets` — Wallet
```sql
id, cif_id, code (unique), name, balance, currency, is_active, created_at, updated_at
```
A wallet owned by a CIF. Has a real balance (BigDecimal, precision 18,2).

#### `users` — Application User
```sql
id, username (unique), email (unique), password (bcrypt), full_name, is_active, created_at, updated_at
```

#### `wallet_users` — User ↔ Wallet membership
```sql
id, wallet_id, user_id, assigned_by, assigned_at, is_active
UNIQUE (wallet_id, user_id)
```
This says "user X has access to wallet Y". Without a row here, the user cannot activate that wallet.

#### `groups` — A role scoped to a wallet
```sql
id, wallet_id, name, description, is_active, created_at
UNIQUE (wallet_id, name)
```
The "Maker" group in Wallet-1 is a **different** group from "Maker" in Wallet-2.
Each wallet manages its own groups independently.

#### `wallet_user_groups` — Which group the user belongs to inside a wallet
```sql
id, wallet_id, user_id, group_id, assigned_by, assigned_at, is_active
UNIQUE (wallet_id, user_id)   ← ONE group per user per wallet
```
This is the **most critical junction table**. It answers: "In wallet X, what group is user Y in?"

#### `features` — What domain area
```sql
id, code (unique), name, is_active
-- Seeded: CASHIN, CASHOUT, TRANSFER, LINK_BANK, UNLINK_BANK
```

#### `functions` — What action type
```sql
id, code (unique), name, is_active
-- Seeded: LIST, READ_DETAIL, CREATE_REQUEST, APPROVE, EXPORT_EXCEL
```

#### `permissions` — Feature × Function combination
```sql
id, feature_id, function_id, code (unique), description
-- code = feature.code + ':' + function.code  e.g. "TRANSFER:APPROVE"
-- 25 rows total (5 × 5 CROSS JOIN)
```

#### `group_permissions` — Which permissions a group has
```sql
id, group_id, permission_id, granted_by, granted_at
UNIQUE (group_id, permission_id)
```

#### `active_wallet_sessions` — One active wallet per user
```sql
id, user_id (unique), wallet_id, jwt_token_id, activated_at, expires_at
```
Only one row per user. When user activates a different wallet, the old row is deleted
and the old token's ID is blacklisted in Redis.

#### `audit_log` — Append-only access log
```sql
id, user_id, wallet_id, feature_code, function_code, permission_code,
target_id, granted (boolean), denial_reason, ip_address, user_agent, created_at
```
Every `@RequiresPermission` check writes a row here — whether granted or denied.

---

## 3. The Permission Matrix

All 25 permissions are generated by a CROSS JOIN at database seed time:

```sql
-- V8__create_permissions.sql
INSERT INTO permissions (feature_id, function_id, code)
SELECT f.id, fn.id, f.code || ':' || fn.code
FROM features f CROSS JOIN functions fn;
```

| Feature \ Function | LIST | READ_DETAIL | CREATE_REQUEST | APPROVE | EXPORT_EXCEL |
|---|---|---|---|---|---|
| **TRANSFER** | TRANSFER:LIST | TRANSFER:READ_DETAIL | TRANSFER:CREATE_REQUEST | TRANSFER:APPROVE | TRANSFER:EXPORT_EXCEL |
| **CASHIN** | CASHIN:LIST | CASHIN:READ_DETAIL | CASHIN:CREATE_REQUEST | CASHIN:APPROVE | CASHIN:EXPORT_EXCEL |
| **CASHOUT** | CASHOUT:LIST | CASHOUT:READ_DETAIL | CASHOUT:CREATE_REQUEST | CASHOUT:APPROVE | CASHOUT:EXPORT_EXCEL |
| **LINK_BANK** | LINK_BANK:LIST | LINK_BANK:READ_DETAIL | LINK_BANK:CREATE_REQUEST | LINK_BANK:APPROVE | LINK_BANK:EXPORT_EXCEL |
| **UNLINK_BANK** | UNLINK_BANK:LIST | UNLINK_BANK:READ_DETAIL | UNLINK_BANK:CREATE_REQUEST | UNLINK_BANK:APPROVE | UNLINK_BANK:EXPORT_EXCEL |

### Default groups seeded in V12

| Group | Permissions |
|---|---|
| **Viewer** | `*:LIST`, `*:READ_DETAIL` (10 permissions) |
| **Maker** | `*:LIST`, `*:READ_DETAIL`, `*:CREATE_REQUEST` (15 permissions) |
| **Checker** | `*:LIST`, `*:READ_DETAIL`, `*:APPROVE` (15 permissions) |
| **Full Operator** | All 25 permissions |
| **Wallet Admin** | Manages users/groups (custom, no feature permissions by default) |

---

## 4. Setup Flow — Step by Step

This is the **admin-time** flow. You run these API calls once to set up the system.

### Step 1: Create CIF

```http
POST /api/cif
{ "code": "CIF-0001", "name": "Company A" }
```

`CifController.create()` → `CifService.create()` → saves `cif` row.

### Step 2: Create Wallet under CIF

```http
POST /api/wallets
{ "cifId": 1, "code": "WALLET-0001", "name": "Main Wallet", "currency": "VND" }
```

Creates `account_wallets` row with `balance=0`, `cif_id=1`.

### Step 3: Create User

```http
POST /api/users
{ "username": "john", "email": "john@co.com", "password": "John@123", "fullName": "John Doe" }
```

Password is hashed with `BCryptPasswordEncoder(12)` before storing.

```http
PATCH /api/users/1/activate
```

Users start inactive. Must explicitly activate.

### Step 4: Assign User to Wallet

```http
POST /api/wallets/assign-user
{ "walletId": 1, "userId": 1 }
```

Creates `wallet_users` row: `(wallet_id=1, user_id=1, is_active=true)`.

> At this point John is **in** the wallet but has **zero permissions**. He has no group yet.

### Step 5: Check Available Permissions

```http
GET /api/permissions
```

Returns all 25 permissions with their IDs. Note the IDs you need for the next step.

### Step 6: Create a Group with Permissions

```http
POST /api/groups
{
  "walletId": 1,
  "name": "Maker",
  "description": "Can create requests",
  "permissionIds": [3, 8, 13]
}
```

This creates:
- One `groups` row: `(wallet_id=1, name="Maker")`
- Three `group_permissions` rows: `(group_id=X, permission_id=3)`, etc.

`permissionIds` here would be `TRANSFER:CREATE_REQUEST`, `CASHIN:CREATE_REQUEST`, `CASHOUT:CREATE_REQUEST`.

### Step 7: Assign User to Group

```http
POST /api/groups/assign-user
{ "walletId": 1, "userId": 1, "groupId": 1 }
```

Creates `wallet_user_groups` row: `(wallet_id=1, user_id=1, group_id=1, is_active=true)`.

Now the chain is complete:
```
John ──► wallet_users ──► Wallet-1
John ──► wallet_user_groups ──► Group "Maker" ──► group_permissions ──► TRANSFER:CREATE_REQUEST
```

---

## 5. Two-Phase Authentication

This system uses **two different JWT tokens**. Understanding why is key to understanding the whole security model.

### Why two tokens?

A user can access multiple wallets. Each wallet has different permissions.
You cannot embed "all permissions for all wallets" in one token — that would be insecure and stale.

Instead:
- **Phase 1**: Verify who you are → get a token proving identity (no permissions yet)
- **Phase 2**: Say which wallet you want to use → get a wallet-scoped token with exact permissions for that wallet

### Phase 1: Login → Pre-Wallet Token

```http
POST /api/auth/login
{ "username": "john", "password": "John@123" }
```

**Inside `AuthServiceImpl.login()`:**

```java
// 1. Find user by username
User user = userRepository.findByUsername(request.getUsername())
        .orElseThrow(() -> new UnauthorizedException("Invalid credentials"));

// 2. Verify password (BCrypt comparison)
if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
    throw new UnauthorizedException("Invalid credentials");
}

// 3. Check user is active
if (!user.isActive()) throw new UnauthorizedException("Account is disabled");

// 4. Load wallets this user can access
List<Long> walletIds = walletUserRepository.findWalletIdsByUserId(user.getId());

// 5. Issue pre-wallet token (no walletId, no permissions in claims)
String accessToken = jwtService.generatePreWalletToken(user);
```

**Response:**
```json
{
  "accessToken": "eyJ...",
  "wallets": [
    { "id": 1, "code": "WALLET-0001", "name": "Main Wallet" }
  ]
}
```

The pre-wallet JWT claims look like:
```json
{
  "sub": "1",
  "userId": 1,
  "type": "PRE_WALLET",
  "exp": 1234567890
}
```

No `walletId`, no `permissions`. This token can **only** be used to activate a wallet.

### Phase 2: Activate Wallet → Wallet Token

```http
POST /api/auth/activate-wallet
Authorization: Bearer <pre-wallet token>
{ "walletId": 1 }
```

**Inside `AuthServiceImpl.activateWallet()`:**

```java
// 1. Confirm user is a member of this wallet
boolean isMember = walletUserRepository
    .existsByWalletIdAndUserIdAndIsActiveTrue(walletId, userId);
if (!isMember) throw new ForbiddenException("You do not have access to this wallet");

// 2. THE KEY QUERY — resolve permissions from DB
List<String> permissions = groupPermissionRepository
    .findPermissionCodesByUserIdAndWalletId(userId, walletId);
// Result: ["TRANSFER:CREATE_REQUEST", "CASHIN:CREATE_REQUEST", "CASHOUT:CREATE_REQUEST"]

// 3. Blacklist old session if exists (wallet switch)
sessionRepository.findByUserId(userId).ifPresent(existing -> {
    redisService.blacklistToken(existing.getJwtTokenId());
    sessionRepository.deleteByUserId(userId);
});

// 4. Generate wallet-scoped JWT with permissions embedded
String tokenId = UUID.randomUUID().toString();
String accessToken = jwtService.generateWalletToken(userId, walletId, permissions, tokenId);

// 5. Save active session record
sessionRepository.save(ActiveWalletSession.builder()
    .userId(userId)
    .walletId(walletId)
    .jwtTokenId(tokenId)   // stored to enable future blacklisting
    .build());

// 6. Cache permissions in Redis
redisService.cachePermissions(userId, walletId, permissions);
```

**Response:**
```json
{
  "walletId": 1,
  "accessToken": "eyJ...",
  "permissions": ["TRANSFER:CREATE_REQUEST", "CASHIN:CREATE_REQUEST"],
  "expiresIn": 3600
}
```

The wallet JWT claims look like:
```json
{
  "sub": "1",
  "userId": 1,
  "walletId": 1,
  "permissions": ["TRANSFER:CREATE_REQUEST", "CASHIN:CREATE_REQUEST"],
  "tokenId": "uuid-here",
  "type": "WALLET",
  "exp": 1234567890
}
```

> **Important**: Permissions are embedded in the JWT at activation time. If an admin changes the
> group's permissions later, John's existing token is **not updated**. John must re-activate the
> wallet to get a fresh token reflecting the new permissions.

---

## 6. Runtime Security Pipeline

Every request to a protected endpoint passes through **three layers** in order:

```
HTTP Request
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  1. JwtAuthFilter  (Servlet Filter)                 │
│     - Validate JWT signature + expiry               │
│     - Check Redis blacklist                         │
│     - Populate AppSecurityContext (thread-local)    │
│     - Populate Spring SecurityContext (authorities) │
└─────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  2. WalletScopeFilter  (Servlet Filter)             │
│     - Only for paths: /api/transfers, /api/cashin   │
│     - Checks AppSecurityContext.getWalletId() != null│
│     - Returns 403 if pre-wallet token used          │
└─────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  3. RbacInterceptor  (MVC HandlerInterceptor)       │
│     - Reads @RequiresPermission on controller method│
│     - Calls AppSecurityContext.hasPermission(code)  │
│     - Writes audit_log row (always, pass or fail)   │
│     - Returns 403 if permission missing             │
└─────────────────────────────────────────────────────┘
     │
     ▼
┌─────────────────────────────────────────────────────┐
│  Controller Method  (Your actual business logic)    │
└─────────────────────────────────────────────────────┘
```

### Filter vs Interceptor — What's the difference?

| | Servlet Filter | MVC HandlerInterceptor |
|---|---|---|
| **Layer** | Below Spring MVC (raw servlet) | Inside Spring MVC |
| **Runs on** | Every request including static files | Only mapped controller routes |
| **Can abort request** | Yes, by not calling `chain.doFilter()` | Yes, by returning `false` from `preHandle()` |
| **Access to handler** | No | Yes — can read controller method annotations |
| **Use in this project** | JwtAuthFilter, WalletScopeFilter | RbacInterceptor |

The key implication: `RbacInterceptor` uses `HandlerInterceptor` because it needs to read the
`@RequiresPermission` annotation on the specific controller method being called. Servlet filters
cannot do this — they don't know which method will handle the request.

---

## 7. Deep Dive: Each Security Layer

### Layer 1: JwtAuthFilter

**File:** `security/JwtAuthFilter.java`

```java
@Override
protected void doFilterInternal(HttpServletRequest request,
                                HttpServletResponse response,
                                FilterChain chain) throws ServletException, IOException {
    try {
        String token = extractToken(request);  // reads "Bearer ..." from Authorization header

        if (token != null && jwtService.isTokenValid(token)) {
            populateContext(token);
        }

        chain.doFilter(request, response);  // continue to next filter

    } finally {
        AppSecurityContext.clear();  // ALWAYS clear thread-local, even on exceptions
    }
}
```

**`isTokenValid()` does three things:**
1. Verify JWT signature (using the secret key)
2. Check token is not expired
3. Check token ID is not in Redis blacklist (was it logged out?)

**`populateContext()` fills two contexts:**
```java
private void populateContext(String token) {
    Long userId      = jwtService.extractUserId(token);
    Long walletId    = jwtService.extractWalletId(token);    // null for pre-wallet
    List<String> permissions = jwtService.extractPermissions(token); // null for pre-wallet

    // Our custom thread-local (fast, no Spring overhead)
    AppSecurityContext.setUserId(userId);
    AppSecurityContext.setWalletId(walletId);
    AppSecurityContext.setPermissions(permissions);

    // Spring's SecurityContext (needed for .anyRequest().authenticated() to work)
    List<SimpleGrantedAuthority> authorities = permissions == null
            ? List.of()
            : permissions.stream().map(SimpleGrantedAuthority::new).toList();

    UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(userId, null, authorities);
    SecurityContextHolder.getContext().setAuthentication(auth);
}
```

> **Two contexts?** Why both `AppSecurityContext` and Spring's `SecurityContextHolder`?
>
> - `SecurityContextHolder` is required so Spring Security's `anyRequest().authenticated()` rule works.
>   Without it, Spring would reject the request as unauthenticated.
> - `AppSecurityContext` is the fast, custom thread-local that `RbacInterceptor` and services use
>   to check permissions without going through Spring's authority system.

### Layer 2: WalletScopeFilter

**File:** `security/WalletScopeFilter.java`

```java
private static final String[] WALLET_SCOPED_PREFIXES = {
    "/api/transfers",
    "/api/cashin",
    "/api/cashout",
};

@Override
protected void doFilterInternal(...) {
    String path = request.getRequestURI();

    if (requiresWalletScope(path) && AppSecurityContext.getWalletId() == null) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(
            "{\"success\":false,\"error\":\"Wallet token required. Activate a wallet first.\"}"
        );
        return;  // short-circuit: stop the filter chain
    }

    chain.doFilter(request, response);
}
```

This filter protects wallet-scoped paths from being accessed with a pre-wallet token.

**What it catches:**
- User logged in but forgot to activate a wallet → `getWalletId()` returns null → 403
- User using an expired wallet token that was reissued as pre-wallet → 403

### Layer 3: RbacInterceptor

**File:** `security/RbacInterceptor.java`

```java
@Override
public boolean preHandle(HttpServletRequest request,
                         HttpServletResponse response,
                         Object handler) throws Exception {

    // Only process actual controller methods (not static resources, etc.)
    if (!(handler instanceof HandlerMethod method)) return true;

    // Read the @RequiresPermission annotation from the method
    RequiresPermission annotation = method.getMethodAnnotation(RequiresPermission.class);
    if (annotation == null) return true;  // no annotation → no check needed

    String  requiredPermission = annotation.value();  // e.g., "TRANSFER:CREATE_REQUEST"
    Long    userId             = AppSecurityContext.getUserId();
    Long    walletId           = AppSecurityContext.getWalletId();
    boolean granted            = AppSecurityContext.hasPermission(requiredPermission);

    // Write audit log REGARDLESS of result (always audit)
    writeAuditLog(userId, walletId, requiredPermission, granted, ...);

    if (!granted) {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.getWriter().write(
            "{\"success\":false,\"error\":\"Access denied: missing permission " + requiredPermission + "\"}"
        );
        return false;  // STOP — do not call the controller method
    }

    return true;  // continue to controller
}
```

**`hasPermission()` is just a list check:**
```java
// AppSecurityContext.java
public static boolean hasPermission(String code) {
    return getPermissions().contains(code);  // O(n) list search
}
```

Permissions were embedded in the JWT at wallet activation time — no database query needed here.

### AppSecurityContext — Thread-Local Pattern

**File:** `security/AppSecurityContext.java`

```java
public final class AppSecurityContext {

    // ThreadLocal: each thread (= each request in a web server) has its OWN copy
    private static final ThreadLocal<Long>         USER_ID     = new ThreadLocal<>();
    private static final ThreadLocal<Long>         WALLET_ID   = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> PERMISSIONS = new ThreadLocal<>();

    // ...setters and getters...

    public static void clear() {
        USER_ID.remove();
        WALLET_ID.remove();
        PERMISSIONS.remove();
    }
}
```

**Why ThreadLocal?**

A web server (Tomcat) handles concurrent requests by running each on a separate thread.
`ThreadLocal<T>` is a variable where each thread has its own independent copy.

```
Thread 1 (John's request)  ──► USER_ID = 1, WALLET_ID = 1, PERMISSIONS = ["TRANSFER:CREATE_REQUEST"]
Thread 2 (Mary's request)  ──► USER_ID = 2, WALLET_ID = 3, PERMISSIONS = ["TRANSFER:APPROVE"]
Thread 3 (Bob's request)   ──► USER_ID = 3, WALLET_ID = 1, PERMISSIONS = []
```

They all write to the same static variable name but each gets a private slot in memory.

**Why `clear()` in a `finally` block?**

Tomcat reuses threads via a thread pool. If you don't clear the ThreadLocal after the request finishes,
the next request that gets the same thread will inherit the previous request's data. This is a serious
security bug. `JwtAuthFilter`'s `finally` block guarantees cleanup regardless of exceptions:

```java
try {
    // ... process request
} finally {
    AppSecurityContext.clear();  // always runs, even if exception thrown
}
```

### The @RequiresPermission Annotation

**File:** `security/RequiresPermission.java`

```java
@Target(ElementType.METHOD)        // can only be placed on methods
@Retention(RetentionPolicy.RUNTIME) // annotation exists at runtime (needed for reflection)
public @interface RequiresPermission {
    String value();  // the permission code, e.g., "TRANSFER:APPROVE"
}
```

How it's used on a controller:

```java
// TransferController.java
@PostMapping("/{requestId}/approve")
@RequiresPermission("TRANSFER:APPROVE")    // <── RbacInterceptor reads this
public ResponseEntity<ApiResponse<TransferRequestResponse>> approve(
        @PathVariable Long requestId,
        @RequestBody ReviewRequest reviewRequest) {
    // ...
}
```

`RbacInterceptor.preHandle()` calls `method.getMethodAnnotation(RequiresPermission.class)` via
Java reflection to read this annotation at request time.

---

## 8. The Key SQL Query

This is the most important query in the whole system. It runs once at wallet activation.

**File:** `repository/GroupPermissionRepository.java`

```java
@Query("""
    SELECT p.code
    FROM GroupPermission gp
    JOIN Permission p        ON p.id  = gp.permissionId
    JOIN WalletUserGroup wug ON wug.groupId  = gp.groupId
    WHERE wug.userId   = :userId
      AND wug.walletId = :walletId
      AND wug.isActive = true
    """)
List<String> findPermissionCodesByUserIdAndWalletId(
        @Param("userId")   Long userId,
        @Param("walletId") Long walletId
);
```

**How to read this query:**

```
WalletUserGroup (wug)
  tells us: "User X is in Group G in Wallet W"

GroupPermission (gp)
  tells us: "Group G has Permission P"

Permission (p)
  tells us: "Permission P has code 'TRANSFER:APPROVE'"

Join them together:
  User X (in Wallet W) → belongs to Group G → which has Permission P → with code "TRANSFER:APPROVE"
```

**Equivalent SQL:**
```sql
SELECT p.code
FROM wallet_user_groups wug
JOIN group_permissions gp ON gp.group_id = wug.group_id
JOIN permissions p         ON p.id        = gp.permission_id
WHERE wug.user_id   = 1     -- John's ID
  AND wug.wallet_id = 1     -- Wallet-1's ID
  AND wug.is_active = true; -- only active group memberships
```

This returns strings like: `["TRANSFER:CREATE_REQUEST", "CASHIN:CREATE_REQUEST"]`

These strings are embedded directly into the JWT and never re-queried during the request lifecycle.

---

## 9. Redis Caching Layer

Redis serves two purposes in the security system:

### Purpose 1: Token Blacklist

When a user logs out or switches wallets, the current token's ID is blacklisted in Redis:

```java
// AuthServiceImpl.java — logout or wallet switch
sessionRepository.findByUserId(userId).ifPresent(existing -> {
    redisService.blacklistToken(existing.getJwtTokenId());  // add to Redis
    sessionRepository.deleteByUserId(userId);
});
```

In `JwtService.isTokenValid()`:
```java
String tokenId = extractTokenId(token);
if (redisService.isTokenBlacklisted(tokenId)) {
    return false;  // token was explicitly invalidated
}
```

This solves the **JWT logout problem**: JWTs are stateless and self-validating, so by default you
cannot invalidate one before it expires. The blacklist gives you that ability.

Redis key pattern: `blacklist:token:{tokenId}` with TTL = token's remaining lifetime.

### Purpose 2: Permission Cache

After wallet activation, permissions are cached:
```java
redisService.cachePermissions(userId, walletId, permissions);
```

Services that need to check permissions (e.g., `PermissionService.userHasPermission()`) check
Redis first before hitting the database:
```java
// Try cache first
List<String> cached = redisService.getPermissions(userId, walletId);
if (cached != null) return cached.contains(permissionCode);

// Fall back to DB
return groupPermissionRepository
    .findPermissionCodesByUserIdAndWalletId(userId, walletId)
    .contains(permissionCode);
```

Redis key pattern: `permissions:{userId}:{walletId}` with TTL = session timeout.

**Cache invalidation** happens on:
- Logout: `redisService.evictPermissions(userId, walletId)`
- Wallet switch: evict old wallet, cache new wallet permissions

---

## 10. Audit Logging

Every `@RequiresPermission` check (whether granted or denied) writes a row to `audit_log`.

**In `RbacInterceptor.writeAuditLog()`:**

```java
// Split "TRANSFER:CREATE_REQUEST" into parts
String[] parts       = permissionCode.split(":");
String featureCode   = parts[0];   // "TRANSFER"
String functionCode  = parts[1];   // "CREATE_REQUEST"

AuditLog log = AuditLog.builder()
    .userId(userId)
    .walletId(walletId)
    .featureCode(featureCode)
    .functionCode(functionCode)
    .permissionCode(permissionCode)
    .granted(granted)               // true or false
    .denialReason(granted ? null : "Permission not assigned to user's group")
    .ipAddress(request.getRemoteAddr())
    .userAgent(request.getHeader("User-Agent"))
    .createdAt(LocalDateTime.now())
    .build();

auditLogRepository.save(log);
```

**Why audit on both grant and deny?**
- Denied access reveals attack patterns (someone probing endpoints they shouldn't access)
- Granted access is a compliance requirement (who did what, when, from where)

**The `try/catch` around it:**
```java
try {
    auditLogRepository.save(log);
} catch (Exception e) {
    log.warn("Failed to write audit log: {}", e.getMessage());
    // never let audit failure block the request
}
```

A failing audit write must never block the user's request. The `catch` eats the exception and
logs a warning. This is intentional: audit is observability, not security enforcement.

---

## 11. Edge Cases and Rules

### Rule 1: User in wallet but no group → zero permissions

If John is added to wallet_users but never assigned to wallet_user_groups,
the key query returns an empty list. John activates the wallet but gets a token with
`"permissions": []`. Every `@RequiresPermission` check will fail → 403.

### Rule 2: One group per user per wallet

The unique constraint `UNIQUE (wallet_id, user_id)` on `wallet_user_groups` enforces this.
If you try to insert a second group for the same user+wallet, the DB rejects it.

`GroupServiceImpl.assignUserToGroup()` handles this:
```java
// If user already has a group in this wallet, UPDATE it (don't INSERT new)
walletUserGroupRepository.findByWalletIdAndUserId(walletId, userId)
    .ifPresentOrElse(
        existing -> {
            existing.setGroupId(groupId);  // change group
            walletUserGroupRepository.save(existing);
        },
        () -> walletUserGroupRepository.save(newAssignment)  // first time
    );
```

### Rule 3: Permission update requires re-activation

When you call `PUT /api/groups/permissions` to change a group's permissions:
1. All old `group_permissions` rows are deleted
2. New ones are inserted
3. But **existing wallet tokens are not invalidated**

John's current token still has the OLD permissions embedded. He must:
```http
POST /api/auth/activate-wallet
{ "walletId": 1 }
```
This re-queries the DB, generates a new token, and blacklists the old one.

### Rule 4: Maker-Checker separation (soft)

`TransferServiceImpl.approve()` has a self-approval check:

```java
// The checker cannot be the same person as the maker
// UNLESS they explicitly have BOTH permissions (Full Operator scenario)
if (request.getCreatedBy().equals(checkerId)) {
    boolean hasBoth = permissionService.userHasPermission(checkerId, walletId, "TRANSFER:CREATE_REQUEST")
                   && permissionService.userHasPermission(checkerId, walletId, "TRANSFER:APPROVE");
    if (!hasBoth) {
        throw new ForbiddenException("Cannot approve your own transfer request");
    }
}
```

- A Maker (CREATE_REQUEST only) cannot approve their own requests
- A Full Operator (has both) can approve their own requests (for small teams / self-service)

### Rule 5: Wallet token required for financial endpoints

`WalletScopeFilter` blocks `/api/transfers`, `/api/cashin`, `/api/cashout` for pre-wallet tokens.
This means even if someone gets a valid pre-wallet token, they cannot make financial calls.
They must explicitly choose a wallet and activate it first.

### Rule 6: Active session is one per user

`active_wallet_sessions` has `UNIQUE (user_id)`. When user activates wallet B while already
on wallet A:
1. Old session (wallet A) is found
2. Old token ID is blacklisted in Redis
3. Old session row is deleted
4. New session (wallet B) is created

This means John can only be "in" one wallet at a time per session.

---

## 12. Mental Model: The Big Picture

### The Three Phases of a Request

**Phase A (Setup — runs once, admin time)**
```
CIF → Wallet → User → [assign user to wallet] → Group → [assign permissions to group] → [assign user to group]
```

**Phase B (Auth — runs at login time)**
```
login → pre-wallet token (proves identity)
     ↓
activate-wallet → wallet token (proves identity + resolves permissions for this wallet)
                   = permission codes embedded in JWT
```

**Phase C (Runtime — runs on every API call)**
```
Request
  → JwtAuthFilter: decode JWT, populate thread-local, validate blacklist
  → WalletScopeFilter: wallet-scoped path? then must have walletId in context
  → RbacInterceptor: method has @RequiresPermission? then check thread-local permissions list
  → Controller: business logic
  → Finally: clear thread-local
```

### Data Flow Diagram

```
SETUP TIME:
  DB:  cif ← account_wallets ← wallet_users ← users
                          ↓
                        groups
                          ↓
               group_permissions → permissions
                          ↑
               wallet_user_groups


LOGIN TIME:
  DB query:  wallet_user_groups JOIN group_permissions JOIN permissions
           → ["TRANSFER:CREATE_REQUEST", ...]
  JWT:       { userId, walletId, permissions: [...], tokenId }
  Redis:     CACHE permissions:{userId}:{walletId}
  DB:        INSERT active_wallet_sessions


REQUEST TIME:
  JWT → AppSecurityContext (thread-local, O(1) reads)
  @RequiresPermission("X") → AppSecurityContext.hasPermission("X") → O(n) List.contains()
  Result → audit_log row
```

### The Performance Trade-off

| When | DB hit? | Description |
|---|---|---|
| Login | Yes | Load wallet list |
| Activate wallet | Yes (once) | Load all permissions from DB, embed in JWT |
| Every API call | **No** | Read permissions from JWT (thread-local, in-memory) |
| Logout | Yes | Delete session, write to Redis blacklist |

The system is designed so the **hot path (API calls)** never hits the database for permission checks.
The cost is paid once at wallet activation.

---

## Quick Reference — Files to Know

| File | What it does |
|---|---|
| `security/AppSecurityContext.java` | Thread-local store: userId, walletId, permissions per request |
| `security/JwtAuthFilter.java` | Decodes JWT, validates, populates AppSecurityContext |
| `security/WalletScopeFilter.java` | Blocks financial endpoints if no walletId in context |
| `security/RbacInterceptor.java` | Reads @RequiresPermission, checks hasPermission(), writes audit log |
| `security/RequiresPermission.java` | The annotation you put on controller methods |
| `config/SecurityConfig.java` | Wires filters + interceptors, defines public paths |
| `service/impl/AuthServiceImpl.java` | login() and activateWallet() — where permissions are resolved |
| `repository/GroupPermissionRepository.java` | The key JPQL query joining 3 tables |
| `db/migration/V7__create_features_functions.sql` | Seeds features and functions |
| `db/migration/V8__create_permissions.sql` | CROSS JOIN to create 25 permissions |
| `db/migration/V12__seed_data.sql` | Seeds default groups with realistic permission sets |