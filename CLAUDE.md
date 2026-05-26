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
| Database | PostgreSQL 16 | UUID PK (`gen_random_uuid()`), 19 bảng |
| Cache/PubSub | Redis 7 | Jedis via Spring Data Redis |
| WebSocket | Spring STOMP + SockJS | Redis STOMP broker relay |
| File Storage | MinIO | S3-compatible, presigned URL |
| Auth | JWT (JJWT, HS512) + httpOnly cookie | Access token 15min, refresh 30 ngày |
| WebRTC | Mesh P2P + Coturn TURN/STUN | Signaling qua WebSocket |
| Testing | JUnit 5 + Mockito + Testcontainers | |
| API Docs | Springdoc OpenAPI 2.x | Swagger UI tại `/swagger-ui.html` |

## Project Structure

```
classpulse/
├── build.gradle.kts
├── docker-compose.yml
└── src/main/java/com/classpulse/
    ├── ClasspulseApplication.java
    ├── config/          # SecurityConfig, WebSocketConfig, RedisConfig, MinioConfig
    ├── common/
    │   ├── exception/   # AppException hierarchy + GlobalExceptionHandler
    │   ├── response/    # ApiResponse<T>, PageMeta
    │   ├── security/    # JwtTokenProvider, JwtAuthFilter, JwtHandshakeHandler, WsTicketService
    │   └── util/        # JoinCodeGenerator
    ├── auth/            # AuthController, AuthService, RefreshTokenService
    ├── user/            # UserController, UserService
    ├── classroom/       # ClassroomController, ClassroomService, ClassroomSecurityBean
    ├── post/            # PostController, PostService
    ├── schedule/        # ScheduleController, ScheduleService
    ├── document/        # DocumentController, DocumentService
    ├── upload/          # UploadController, UploadService (presigned URL)
    ├── session/         # SessionController, SessionService, SessionBroadcastService, SessionSecurityBean
    ├── question/        # QuestionController, QuestionService, QuestionTimerService, SilentStudentDetector
    ├── breakout/        # BreakoutController, BreakoutService
    ├── chat/            # ChatController, ChatWsController, ChatService
    ├── dashboard/       # DashboardController, DashboardService, SessionSummaryComputeJob
    ├── review/          # StudentReviewController, StudentReviewService
    └── admin/           # AdminController, AdminService
```

Package convention: `com.classpulse.<module>.<layer>` (feature-first).

## Design Docs (`ClassPulseDoc/`)

| File | Nội dung |
|------|---------|
| `01_System_Overview.md` | Roles, features, 5 main workflows |
| `02_Database_Design.md` | 19 bảng, ERD, DDL, index strategy |
| `03_API_Design.md` | ~58 REST endpoints + WebSocket event contract |
| `04_Realtime_Architecture.md` | STOMP, Redis Pub/Sub, WebRTC signaling, timer |
| `05_Auth_Authorization.md` | JWT flow, SecurityConfig, RBAC, WS Ticket |
| `06_System_Architecture.md` | Tech stack, folder structure, Docker Compose |
| `07_Best_Practices.md` | Naming, error handling, logging, transaction |
| `plan/implementation_plan.md` | Sprint plan, task list (T001–T098), risks |

## Key Architecture Decisions

- **Modular Monolith** — không phải microservices.
- **JWT stateless** — access token trong memory (React state), refresh token trong httpOnly cookie.
- **WS Ticket** — one-time Redis token (60s TTL). Ticket **phải truyền qua URL query param** `?ticket=xxx` trong SockJS URL — `JwtHandshakeHandler.extractTicket()` đọc từ HTTP query string tại HTTP upgrade; không đọc được STOMP `connectHeaders`.
- **WS Session Attributes** — `JwtHandshakeHandler` lưu vào `attributes`: `userId`, `userRole`, `sessionId`, `userName`, `userAvatarColor`. `PresenceEventListener` đọc trực tiếp từ đây để không cần DB lookup thêm.
- **Presence event timing** — `PresenceEventListener` dùng `SessionConnectEvent` (CONNECT frame đến), **trước** khi STOMP CONNECTED frame gửi về → student mới chưa subscribe `/user/queue/private`. Frontend xử lý WebRTC offer trong `onConnected` callback, không phải trong handler của `student_presence`.
- **Lombok boolean + Jackson** — field `private boolean isXxx` với `@Getter` sinh `isXxx()`. Jackson bỏ prefix `is` → JSON field thành `xxx`. Nếu frontend expect `isXxx`, phải annotate `@JsonProperty("isXxx")` trên field (xem `PresenceDto.isOnline`).
- **WebSocket CORS** — `WebSocketConfig.setAllowedOriginPatterns` (SockJS-level, tách biệt Spring Security CORS) cho phép `http://localhost:*`, `http://192.168.*:*`, `http://10.*:*`. Thiếu LAN pattern → phone bị chặn tại HTTP upgrade.
- **Server-side timer** — `QuestionTimerService` dùng `ScheduledExecutorService`. Client countdown từ `endsAt` timestamp, không tin client clock.
- **Presigned URL** — file upload thẳng lên MinIO, không qua Spring server.
- **Precomputed summaries** — `session_student_summaries` tính async sau session ended, dashboard chỉ SELECT.
- **STOMP destinations**: `/topic/session/{id}` (broadcast), `/topic/session/{id}/room/{roomId}` (breakout), `/user/queue/private` (unicast).
- **TURN server** — Coturn `lt-cred-mech`, credentials `classpulse`/`secret123`. Production: đổi sang `use-auth-secret` + TLS.

## API Conventions

Base URL: `/api/v1`. Auth levels: `[PUBLIC]` `[AUTH]` `[TEACHER]` `[STUDENT]` `[OWNER]` `[ADMIN]`

```json
{ "success": true, "data": {...}, "meta": {...} }
{ "success": false, "error": { "code": "ERROR_CODE", "message": "..." } }
```

`[OWNER]` = teacher phải là chủ lớp/session — `@PreAuthorize("@classroomSecurity.isOwner(...)")`.

## Database

- Tất cả PK là `UUID` (`gen_random_uuid()`) — không dùng BIGSERIAL
- `selected_option_ids UUID[]` — PostgreSQL native array, cần `hibernate-types`
- Partial indexes: `WHERE status = 'running'`, `WHERE status = 'active'`
- Unique partial index: `UNIQUE (classroom_id) WHERE status = 'active'` trên `sessions` (V11)
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
- **Validation**: `@Valid` trên tất cả `@RequestBody`. Bean Validation trên DTO fields
- **Naming**: `CreateClassroomRequest`, `ClassroomDto`, `ClassroomService` — PascalCase, theo module
- **Logging**: `@Slf4j`, `INFO` cho CRUD, `WARN` cho business rule violation, `ERROR` cho unexpected

## Dev Setup

```bash
cp .env.example .env          # điền JWT_SECRET (chỉ lần đầu)
docker-compose up -d          # start infrastructure
./gradlew bootRun             # run app (auto-load .env, SPRING_PROFILES_ACTIVE=dev)
./gradlew test                # Testcontainers tự spin up DB + Redis
./gradlew bootJar             # build jar
```

## Implementation Status

**Tất cả T001–T098 đã complete.** Chi tiết từng task xem `ClassPulseDoc/plan/implementation_plan.md`.

| Milestone | Mô tả | Tasks |
|-----------|-------|-------|
| M01 Infrastructure | Gradle, Docker, config, exceptions, OpenAPI | T001–T010 |
| M02 Auth | JWT, refresh token, WS ticket, login/register | T011–T025 |
| M03 User | Profile, avatar upload, admin user mgmt | T026–T031 |
| M04 Classroom | CRUD, join/leave, members, join code | T032–T039 |
| M05 Post/Feed | Posts, attachments, pagination | T040–T044 |
| M06 Schedule | Schedule CRUD, nextSchedule | T045–T047 |
| M07 Document | Classroom docs, MinIO upload | T048–T050 |
| M08 Upload | Presigned URL generation | T051–T052 |
| M09 Session | Start/join/end, presence tracking | T053–T062 |
| M10 Question | MCQ/essay, timer, silent detector | T063–T071 |
| M11 Student Answer | Submit, view answers | T072–T074 |
| M12 Breakout | Rooms, assignments, broadcast | T075–T079 |
| M13 Realtime/WS | STOMP, presence, chat, raise hand, WebRTC | T080–T091 |
| M14 Dashboard | Session summary compute, stats API | T092–T094 |
| M15 Student Review | Post-session review per student | T095–T096 |
| M16 Admin | Stats, classroom list | T097–T098 |

**Next:** Sprint 7 — Testing & Hardening
