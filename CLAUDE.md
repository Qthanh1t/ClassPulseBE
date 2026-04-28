# ClassPulse — CLAUDE.md

Đồ án tốt nghiệp. Nền tảng tương tác realtime cho lớp học nhỏ (≤ 30 học sinh).

## Tech Stack

| Layer | Tech | Notes |
|-------|------|-------|
| Runtime | Java 21 | Virtual threads (Project Loom) |
| Framework | Spring Boot 3.x | Spring Security 6, WebMVC |
| Build | **Gradle 8.x (Kotlin DSL)** | `build.gradle.kts` — không dùng Maven |
| ORM | Spring Data JPA + Hibernate 6 | `ddl-auto: validate` — Flyway quản lý schema |
| Migration | Flyway 10.x | `src/main/resources/db/migration/V{n}__*.sql` |
| Database | PostgreSQL 16 | UUID PK (`gen_random_uuid()`), tất cả 19 bảng |
| Cache/PubSub | Redis 7 | Jedis via Spring Data Redis |
| WebSocket | Spring STOMP + SockJS | Redis STOMP broker relay |
| File Storage | MinIO | S3-compatible, presigned URL — file không đi qua server |
| Auth | JWT (JJWT, HS512) + httpOnly cookie | Access token 15min, refresh 30 ngày |
| WebRTC | Mesh P2P + Coturn TURN/STUN | Signaling qua WebSocket |
| Testing | JUnit 5 + Mockito + Testcontainers | |
| API Docs | Springdoc OpenAPI 2.x | Swagger UI tại `/swagger-ui.html` |

## Project Structure

```
classpulse/
├── build.gradle.kts
├── settings.gradle.kts
├── docker-compose.yml
├── src/main/java/com/classpulse/
│   ├── ClasspulseApplication.java
│   ├── config/          # SecurityConfig, WebSocketConfig, RedisConfig, MinioConfig, CorsConfig
│   ├── common/
│   │   ├── exception/   # AppException hierarchy + GlobalExceptionHandler
│   │   ├── response/    # ApiResponse<T>, PageMeta
│   │   ├── security/    # JwtTokenProvider, JwtAuthFilter, JwtHandshakeHandler, WsTicketService
│   │   └── util/        # JoinCodeGenerator
│   ├── auth/            # AuthController, AuthService, RefreshTokenService
│   ├── user/            # UserController, UserService
│   ├── classroom/       # ClassroomController, ClassroomService, ClassroomSecurityBean
│   ├── post/            # PostController, PostService
│   ├── schedule/        # ScheduleController, ScheduleService
│   ├── document/        # DocumentController, DocumentService
│   ├── upload/          # UploadController, UploadService (presigned URL)
│   ├── session/         # SessionController, SessionService, SessionBroadcastService, SessionSecurityBean
│   ├── question/        # QuestionController, QuestionService, QuestionTimerService, SilentStudentDetector
│   ├── breakout/        # BreakoutController, BreakoutService
│   ├── chat/            # ChatController, ChatWsController, ChatService
│   ├── dashboard/       # DashboardController, DashboardService, SessionSummaryComputeJob
│   └── admin/           # AdminController, AdminService
└── src/main/resources/
    ├── application.yml
    ├── application-dev.yml
    └── db/migration/    # V1__create_users.sql ... V10__create_realtime.sql
```

Package convention: `com.classpulse.<module>.<layer>` (feature-first, không phải layer-first).

## Design Docs

Tài liệu đầy đủ trong `ClassPulseDoc/`:

| File | Nội dung |
|------|---------|
| `01_System_Overview.md` | Roles, features, 5 main workflows |
| `02_Database_Design.md` | 19 bảng, ERD, DDL, index strategy |
| `03_API_Design.md` | ~58 REST endpoints + WebSocket event contract |
| `04_Realtime_Architecture.md` | STOMP, Redis Pub/Sub, WebRTC signaling, timer |
| `05_Auth_Authorization.md` | JWT flow, SecurityConfig, RBAC, WS Ticket |
| `06_System_Architecture.md` | Tech stack, folder structure, Docker Compose |
| `07_Best_Practices.md` | Naming, error handling, logging, transaction |
| `plan/implementation_plan.md` | Sprint plan, task list (T001-T098), risks |

## Key Architecture Decisions

- **Modular Monolith** — không phải microservices. Team nhỏ, WS dễ quản lý hơn.
- **JWT stateless** — access token trong memory (React state), refresh token trong httpOnly cookie.
- **WS Ticket** — one-time Redis token (60s TTL) để auth WebSocket handshake (không dùng JWT header cho WS).
- **Server-side timer** — `QuestionTimerService` dùng `ScheduledExecutorService`. Client countdown từ `endsAt` timestamp của server, không tin client clock.
- **Presigned URL** — file upload thẳng lên MinIO, không đi qua Spring server.
- **Precomputed summaries** — `session_student_summaries` được tính async sau khi session ended, dashboard chỉ SELECT.
- **STOMP destinations**: `/topic/session/{id}` (broadcast all), `/topic/session/{id}/room/{roomId}` (breakout room), `/user/queue/private` (unicast).

## API Conventions

Base URL: `/api/v1`

Response envelope luôn là:
```json
{ "success": true, "data": {...}, "meta": {...} }
{ "success": false, "error": { "code": "ERROR_CODE", "message": "..." } }
```

Auth levels: `[PUBLIC]` `[AUTH]` `[TEACHER]` `[STUDENT]` `[OWNER]` `[ADMIN]`

`[OWNER]` = teacher phải là chủ lớp/session — dùng `@PreAuthorize("@classroomSecurity.isOwner(...)")`.

## Database

- Tất cả PK là `UUID` (`gen_random_uuid()`) — không dùng BIGSERIAL
- `selected_option_ids UUID[]` — PostgreSQL native array, cần `hibernate-types` library
- Partial indexes quan trọng: `WHERE status = 'running'`, `WHERE status = 'active'`
- Flyway chạy tự động khi app start. Không sửa migration đã commit — tạo migration mới.

## Redis Keys

| Pattern | Type | TTL |
|---------|------|-----|
| `ws_ticket:{ticket}` | String | 60s |
| `session:{id}:presence` | Set | session duration |
| `session:{id}:raised_hands` | Set | session duration |
| `session:{id}:active_question` | String | 5min |
| `session:{id}:question:{qid}:answered` | Set | 5min |

## Coding Conventions

- **Exception**: throw `NotFoundException`, `ConflictException`, `BusinessException` — không return null
- **Transaction**: `@Transactional` tại service layer. Broadcast WS event SAU khi service return (trong controller), không trong transaction
- **Validation**: `@Valid` trên tất cả `@RequestBody`. Bean Validation annotations trên DTO fields
- **Naming**: `CreateClassroomRequest`, `ClassroomDto`, `ClassroomService`, `ClassroomRepository` — PascalCase, theo module
- **Logging**: `@Slf4j`, log `INFO` cho CRUD quan trọng, `WARN` cho business rule violation, `ERROR` cho unexpected

## Dev Setup

```bash
# 1. Copy env và điền JWT_SECRET (chỉ lần đầu)
cp .env.example .env

# 2. Start infrastructure
docker-compose up -d

# 3. Run app — build.gradle.kts tự load .env, SPRING_PROFILES_ACTIVE=dev đã có trong .env
./gradlew bootRun

# Run tests (Testcontainers tự spin up DB + Redis riêng, không cần docker-compose)
./gradlew test

# Build jar
./gradlew bootJar
```

Env vars đọc từ `.env` (auto-loaded bởi `bootRun` task trong `build.gradle.kts`). Xem `.env.example` để biết danh sách đầy đủ. Biến bắt buộc không có default: `JWT_SECRET`.

## Current Implementation Status

Xem `ClassPulseDoc/plan/implementation_plan.md` để biết task nào đã xong / đang làm.
Sprint plan: 7 sprints × 2 tuần. Tasks: T001–T098.

### Completed

#### M01 — Infrastructure ✅ (T001–T010)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T001 | Init Gradle project — Web, Security, JPA, WebSocket, Redis/Jedis, Flyway, JWT, Springdoc, Lombok, Hypersistence, Testcontainers | `build.gradle.kts` |
| T002 | Docker Compose — Postgres 16, Redis 7, MinIO, Coturn + healthchecks | `docker-compose.yml`, `turnserver.conf`, `.env.example` |
| T003 | application.yml — datasource/HikariCP, JPA, Flyway, Redis/Jedis, JWT, MinIO, virtual threads | `application.yml`, `application-dev.yml` |
| T004 | BaseEntity — `@MappedSuperclass`, UUID PK, `createdAt`/`updatedAt` JPA Auditing | `common/BaseEntity.java`, `config/JpaConfig.java` |
| T005 | ApiResponse wrapper — `ApiResponse<T>` (ok/error factories, NON_NULL), `PageMeta` | `common/response/ApiResponse.java`, `common/response/PageMeta.java` |
| T006 | Exception hierarchy — `AppException`, `NotFoundException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `BusinessException` | `common/exception/*.java` |
| T007 | GlobalExceptionHandler — xử lý AppException, validation, JSON parse, AccessDenied, fallback 500 | `common/exception/GlobalExceptionHandler.java` |
| T008 | RequestLoggingFilter — MDC requestId, log method/path/status/duration, `X-Request-ID` header | `common/RequestLoggingFilter.java` |
| T009 | JoinCodeGenerator — 6-char SecureRandom, bỏ ký tự nhầm lẫn O/0/I/1 | `common/util/JoinCodeGenerator.java` |
| T010 | OpenAPI config — Bearer JWT scheme, global security, Swagger UI `/swagger-ui.html` | `config/OpenApiConfig.java` |

#### M02 — Auth (T011–T025)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T011 | Flyway V1: users — DDL `users` table + indexes | `db/migration/V1__create_users.sql` |
| T012 | Flyway V2: refresh_tokens — DDL `refresh_tokens` table + indexes | `db/migration/V2__create_refresh_tokens.sql` |
| T013 | User entity + Role enum — `@Entity` cho `users`, `Role` enum (TEACHER/STUDENT/ADMIN) với `AttributeConverter` sang lowercase | `user/User.java`, `user/Role.java` |
| T014 | UserRepository — `JpaRepository<User, UUID>` + `findByEmail`, `existsByEmail` | `user/UserRepository.java` |
| T015 | JwtTokenProvider — generate HS512 access token (sub/role/name/jti), validate, parseClaims, helper getters | `common/security/JwtTokenProvider.java` |
| T016 | JwtAuthFilter — extract Bearer token → parseClaims → build `UserPrincipal` → set SecurityContext; `UserPrincipal` record (userId, role, name) dùng với `@AuthenticationPrincipal` | `common/security/JwtAuthFilter.java`, `common/security/UserPrincipal.java` |
| T017 | SecurityConfig — stateless, CSRF off, CORS, public routes, addFilterBefore JwtAuthFilter, `PasswordEncoder` BCrypt(12), `@EnableMethodSecurity` | `config/SecurityConfig.java` |
| T018 | JwtAuthEntryPoint + JwtAccessDeniedHandler — JSON 401/403 dùng `ObjectMapper` + `ApiResponse.error(...)` | `common/security/JwtAuthEntryPoint.java`, `common/security/JwtAccessDeniedHandler.java` |
| T019 | Auth DTOs — `RegisterRequest` (@Valid: email/password/name/role), `LoginRequest`, `AuthResponse` (user+accessToken+expiresIn) với `UserSummary.from(User)` factory | `auth/RegisterRequest.java`, `auth/LoginRequest.java`, `auth/AuthResponse.java` |
| T020 | RefreshToken entity + repo — không extend BaseEntity (không có updated_at); `findByTokenHashAndRevokedFalse`, `revokeAllByUserId` (@Modifying bulk update) | `auth/RefreshToken.java`, `auth/RefreshTokenRepository.java` |
| T021 | RefreshTokenService — `createRefreshToken` (SHA-256 hash, 30-day expiry), `validateAndConsume` (rotation: revoke cũ + tạo mới), `revokeByRawToken`, `revokeAllForUser` | `auth/RefreshTokenService.java`, `auth/AuthResult.java` |
| T022 | WsTicketService — Redis `ws_ticket:{ticket}` với TTL 60s, `generateTicket(userId)`, `validateAndConsume(ticket) → Optional<UUID>` | `common/security/WsTicketService.java` |
| T023 | AuthService — `register` (uniqueness check, bcrypt, save user, issue tokens), `login` (verify pw, issue tokens), `refresh` (rotate token, new JWT), `logout` (revoke token) | `auth/AuthService.java` |
| T024 | AuthController — 5 endpoints: POST /register, /login, /refresh, /logout, /ws-ticket; httpOnly cookie `refresh_token`; `app.cookie.secure` config | `auth/AuthController.java`, `config/SecurityConfig.java` (logout permitAll) |
| T025 | Auth integration test — Testcontainers (Postgres 16 + Redis 7), MockMvc: register→login→refresh→logout happy path + token rotation + duplicate email + wrong password | `auth/AuthIntegrationTest.java` |

#### M03 — User (T026)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T026 | UserDto + UpdateProfileRequest — `UserDto` với nested `Stats` (classroomsCount, sessionsCount, questionsAsked, studentsReached), `@JsonInclude(NON_NULL)`, 2 factory `from(User)` / `from(User, Stats)`; `UpdateProfileRequest` (@Valid: name, optional avatarColor hex pattern) | `user/UserDto.java`, `user/UpdateProfileRequest.java` |

### In Progress

_(none)_

### Next

T027 — UserService (getMe)
