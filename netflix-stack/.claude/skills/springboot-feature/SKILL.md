---
name: springboot-feature
description: >
  Senior-level Java 21 / Spring Boot 3 code in a fixed layered house style —
  Lombok, service interface + Impl, Spring Data JPA everywhere, MapStruct
  mappers, DTO classes (never records), enums instead of string literals,
  ApiResponse envelope, @RestControllerAdvice error handling, Bean Validation,
  paged list endpoints, and zero comments in the generated code. Use this skill
  for ANY Java or Spring Boot work: scaffolding a new CRUD feature or module,
  writing or changing an entity, repository, service, controller, DTO, mapper,
  enum, exception handler, search or pagination endpoint, and also when
  reviewing or refactoring existing Spring Boot code. Trigger it even when the
  user only pastes a requirement, a table definition, or an existing class and
  asks for "the API" or "the code" without naming any convention. Do not use it
  for non-JVM backends, and defer to a project-specific ruleset (such as
  leed-rules) when one covers the same repository.
---

# Spring Boot Feature Scaffolding

Generate Spring Boot code the way a senior engineer on this codebase would: one
predictable file per layer, no ceremony that isn't earning its place, and no
prose inside the source.

## Target stack

Java 21, Spring Boot 3.x, Maven, Spring Data JPA (Hibernate 6), Lombok,
MapStruct, Bean Validation. The `jakarta.*` namespace throughout — writing
`javax.persistence` or `javax.validation` means the code is aimed at the wrong
Boot generation and will not compile here.

Prefer what the project already has over what this skill describes. Read the
existing base package, `pom.xml`, and any `common/` classes first; if the
codebase already ships an envelope, a base entity, or an error enum under
different names, extend those instead of introducing a parallel set.

## Package layout

```
com.<org>.<app>
├── common/      ApiResponse, PageResponse, ErrorCode, BusinessException,
│                GlobalExceptionHandler, BaseEntity, cross-cutting enums
├── config/      @Configuration beans — security, jackson, openapi, async, cache
├── controller/  @RestController, thin
├── dto/
│   ├── request/ inbound payloads, validated
│   └── response/outbound payloads
├── entity/      @Entity classes and the enums they own
├── mapper/      MapStruct interfaces
├── repository/  Spring Data interfaces and Specification helpers
├── service/     interfaces
│   └── impl/    implementations
└── util/        stateless static helpers, package-private constructors
```

A feature produces at most one file per layer. Resist inventing layers —
no `manager`, `helper`, `facade`, or `handler` tier unless the user asks for
one. Extra indirection is the most common way a clean scaffold turns into a
maze nobody wants to touch six months later.

## The house rules

### 1. Lombok, deliberately

`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder` on entities
and DTOs. `@RequiredArgsConstructor` with `private final` fields on services,
controllers, and components.

Avoid `@Data` on entities. It generates `equals`/`hashCode` across every field,
which misbehaves against Hibernate proxies and mutable generated ids, and a
`toString` that walks lazy associations — enough to trigger extra queries or
infinite recursion on a bidirectional mapping. On DTOs `@Data` is harmless, but
staying with explicit `@Getter @Setter` keeps one idiom across the codebase.

Never `@Autowired` a field. Constructor injection makes the dependency list
readable at a glance, allows `final`, and lets the class be unit-tested with
plain `new` and no Spring context.

### 2. Interface plus implementation

`ProductService` lives in `service`, `ProductServiceImpl` in `service.impl` and
carries `@Service`. The interface declares only what callers need and stays free
of Spring annotations; `@Transactional` belongs on the implementation, where the
transaction boundary actually is.

### 3. JPA first

Reach for the highest-level tool that expresses the query:

1. derived query methods (`findByStatusAndCreatedAtAfter`)
2. `@Query` JPQL once the derived name stops being readable
3. `Specification` for dynamic, user-driven filters
4. native SQL only when JPQL genuinely cannot express it — and say so in chat,
   not in the file

Non-negotiables that prevent the usual production surprises:

- every `@ManyToOne` and `@OneToOne` gets `fetch = FetchType.LAZY`; the JPA
  default is EAGER for to-one associations and it silently produces N+1
- pull associations at the query site with `@EntityGraph` or `join fetch`
- `@Transactional(readOnly = true)` on read paths, plain `@Transactional` on
  writes
- `@Enumerated(EnumType.STRING)` — ORDINAL breaks the day someone reorders the
  constants
- `@Table` with explicit name, and unique/index declarations where the domain
  requires them

### 4. DTO classes, never records

DTOs are Lombok classes. That keeps one construction idiom across the codebase,
keeps a no-arg constructor available for Jackson and mapping tooling, and leaves
room for a `@Setter`-based partial update. Requests and responses are separate
types — a create payload and a read payload drift apart faster than expected.

Entities never cross the controller boundary in either direction.

### 5. Enums instead of string literals

Any fixed set of values is an enum: status, type, role, error code. Domain enums
live in `entity`, cross-cutting ones in `common`. Enums may carry fields and
behaviour — `ErrorCode` holding an HTTP status and a message template is the
canonical example. A string literal standing in for a meaningful value is a bug
waiting for a typo.

### 6. MapStruct mappers

```java
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
```

`ERROR` is the point: when a field is added to a response DTO and nobody maps it,
the build fails instead of shipping a silent `null`. Mappers expose
`toEntity`, `toResponse`, `toResponseList`, and for updates a
`void update(@MappingTarget Product entity, ProductUpdateRequest request)` with
`nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE`.

### 7. Thin controllers

Validate, delegate, wrap. No business logic, no repository, no `try/catch`.
`@Valid` on request bodies, `@Validated` on the class when parameters carry
constraints. Every method returns `ApiResponse<T>`. List endpoints accept
`Pageable` and return `PageResponse<T>` so the client always sees the same
pagination shape.

### 8. Errors travel as exceptions

Services throw `BusinessException(ErrorCode.PRODUCT_NOT_FOUND)`. One
`@RestControllerAdvice` translates exceptions — business, validation,
constraint, and unexpected — into the envelope. A service that returns `null` to
mean "not found", or that builds an error response itself, spreads HTTP concerns
into the domain and forces every caller to re-check.

### 9. No comments in the code

Names carry the meaning. If a line seems to need a comment, the fix is a better
name or an extracted method. No Javadoc either unless the user asks for it on a
published API. Anything genuinely subtle — a native query, a locking choice, a
performance trade-off — goes in the chat reply, where it can be discussed,
rather than in a file where it will rot.

### 10. What makes it read as senior

Constructor injection and `final` fields. Transactions at the service boundary,
never in the controller or repository. Validation at the edge, invariants in the
domain. `Optional` for lookups, never as a field or parameter type. Guard
clauses instead of nested conditionals. Streams where they clarify and loops
where they don't. No abstraction introduced for a second caller that doesn't
exist yet.

## Workflow

1. **Read before writing.** Base package, existing `common/` classes, an
   existing entity and controller for local dialect. Match what's there.
2. **Settle the model.** Fields and types, relations and their sides, enums,
   unique constraints, which fields are updatable. Ask once if the requirement
   leaves this ambiguous; assume and state the assumption if the user is not
   around to answer.
3. **Generate bottom-up:** entity and its enums → repository → request/response
   DTOs → mapper → service interface → implementation → controller. Each layer
   then compiles against something real rather than a placeholder.
4. **Fill the foundation.** If `ApiResponse`, `PageResponse`, `ErrorCode`,
   `BusinessException`, `GlobalExceptionHandler`, or `BaseEntity` are missing,
   create them from `references/foundation.md` before the feature files.
5. **Verify.** Run `./mvnw -q compile` when the project builds locally. Then walk
   the checklist below.

## Self-review checklist

- no `javax.*` import, no `@Autowired` field, no `@Data` on an entity
- every to-one association is `LAZY`; list queries that touch associations use
  `@EntityGraph` or `join fetch`
- `@Transactional` present on writes, `readOnly = true` on reads
- no entity type in any controller signature
- request DTOs validated; list endpoint paged
- no string literal where an enum belongs; enums persisted as `STRING`
- mapper covers every target field
- not a single comment or Javadoc block in the output

## References

Read these while generating rather than reciting from memory:

- `references/foundation.md` — the `common/` infrastructure: `ApiResponse`,
  `PageResponse`, `ErrorCode`, `BusinessException`, `GlobalExceptionHandler`,
  `BaseEntity`, and the Maven dependency/plugin block that makes Lombok and
  MapStruct coexist.
- `references/templates.md` — canonical file per layer for one feature, end to
  end. Read before writing the first file of a new feature.
- `references/jpa-recipes.md` — dynamic search with `Specification`, `@EntityGraph`
  against N+1, auditing, soft delete, optimistic locking, bulk operations,
  and enum converters.