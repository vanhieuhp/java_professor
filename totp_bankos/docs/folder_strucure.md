totp_bankos/
├── src/main/java/dev/hieunv/totp_bankos/
│   │
│   ├── config/                         -- Spring configs
│   │     ├── SecurityConfig.java
│   │     ├── RedisConfig.java
│   │     └── FlywayConfig.java
│   │
│   ├── domain/                         -- Pure domain entities (JPA)
│   │     ├── Cif.java
│   │     ├── AccountWallet.java
│   │     ├── User.java
│   │     ├── WalletUser.java
│   │     ├── Group.java
│   │     ├── WalletUserGroup.java
│   │     ├── Feature.java
│   │     ├── Function.java
│   │     ├── Permission.java
│   │     ├── GroupPermission.java
│   │     ├── ActiveWalletSession.java
│   │     └── AuditLog.java
│   │
│   ├── dto/                            -- Request / Response objects
│   │     ├── request/
│   │     │     ├── LoginRequest.java
│   │     │     ├── ActivateWalletRequest.java
│   │     │     ├── AssignUserToWalletRequest.java
│   │     │     ├── AssignUserToGroupRequest.java
│   │     │     └── CreateGroupRequest.java
│   │     └── response/
│   │           ├── LoginResponse.java
│   │           ├── WalletTokenResponse.java
│   │           ├── UserPermissionsResponse.java
│   │           └── WalletSummaryResponse.java
│   │
│   ├── mapper/                         -- MapStruct mappers
│   │     ├── UserMapper.java
│   │     ├── WalletMapper.java
│   │     └── PermissionMapper.java
│   │
│   ├── repository/                     -- Spring Data JPA repos
│   │     ├── CifRepository.java
│   │     ├── AccountWalletRepository.java
│   │     ├── UserRepository.java
│   │     ├── WalletUserRepository.java
│   │     ├── GroupRepository.java
│   │     ├── WalletUserGroupRepository.java
│   │     ├── PermissionRepository.java
│   │     ├── GroupPermissionRepository.java
│   │     ├── ActiveWalletSessionRepository.java
│   │     └── AuditLogRepository.java
│   │
│   ├── service/                        -- Interfaces
│   │     ├── AuthService.java
│   │     ├── WalletService.java
│   │     ├── GroupService.java
│   │     ├── PermissionService.java
│   │     └── AuditService.java
│   │
│   ├── service/impl/                   -- Implementations
│   │     ├── AuthServiceImpl.java
│   │     ├── WalletServiceImpl.java
│   │     ├── GroupServiceImpl.java
│   │     ├── PermissionServiceImpl.java
│   │     └── AuditServiceImpl.java
│   │
│   ├── security/                       -- RBAC enforcement
│   │     ├── SecurityContext.java
│   │     ├── JwtAuthFilter.java
│   │     ├── WalletScopeFilter.java
│   │     ├── RbacInterceptor.java
│   │     └── RequiresPermission.java
│   │
│   ├── controller/                     -- REST endpoints
│   │     ├── AuthController.java
│   │     ├── WalletController.java
│   │     ├── TransferController.java
│   │     ├── CashinController.java
│   │     └── GroupController.java
│   │
│   └── exception/                      -- Error handling
│         ├── UnauthorizedException.java
│         ├── ForbiddenException.java
│         └── GlobalExceptionHandler.java
│
├── src/main/resources/
│   ├── application.yml
│   └── db/migration/                   -- Flyway SQL files
│         ├── V1__create_cif.sql
│         ├── V2__create_account_wallets.sql
│         ├── V3__create_users.sql
│         ├── V4__create_wallet_users.sql
│         ├── V5__create_groups.sql
│         ├── V6__create_wallet_user_groups.sql
│         ├── V7__create_features_functions.sql
│         ├── V8__create_permissions.sql
│         ├── V9__create_group_permissions.sql
│         ├── V10__create_active_wallet_sessions.sql
│         ├── V11__create_audit_log.sql
│         └── V12__seed_data.sql
│
└── pom.xml