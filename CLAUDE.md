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

#### M03 — User (T026–T030)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T026 | UserDto + UpdateProfileRequest — `UserDto` với nested `Stats` (classroomsCount, sessionsCount, questionsAsked, studentsReached), `@JsonInclude(NON_NULL)`, 2 factory `from(User)` / `from(User, Stats)`; `UpdateProfileRequest` (@Valid: name, optional avatarColor hex pattern) | `user/UserDto.java`, `user/UpdateProfileRequest.java` |
| T027 | UserService (getMe) — load user by UUID, build `UserDto` với stats; stats trả 0 cho đến khi M04/M09/M10 triển khai | `user/UserService.java` |
| T028 | UserService (updateProfile) — update name + avatarColor (nếu có), trả `UserDto` đã cập nhật | `user/UserService.java` |
| T029 | UserService (uploadAvatar) — validate ext (jpg/png/webp) + size (max 5MB), upload lên MinIO `avatars/{userId}.{ext}`, update `avatar_url`, trả avatarUrl string | `user/UserService.java` |
| T030 | MinioConfig — `MinioClient` bean; tạo bucket nếu chưa có + set public-read policy; thêm `io.minio:minio:8.5.12` vào `build.gradle.kts` | `config/MinioConfig.java`, `build.gradle.kts` |
| T031 | UserController — 5 endpoints: `GET /me`, `PUT /me`, `POST /me/avatar`, `GET /users` (ADMIN, paginated + role/search filter), `PUT /users/:id` (ADMIN ban/role); `AdminUpdateUserRequest` DTO; `findFiltered` JPQL query trong `UserRepository` | `user/UserController.java`, `user/AdminUpdateUserRequest.java`, `user/UserRepository.java`, `user/UserService.java` |

#### M04 — Classroom (T032–T039)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T032 | Flyway V3: classrooms + memberships — DDL `classrooms` + `classroom_memberships`, indexes | `db/migration/V3__create_classrooms.sql` |
| T033 | Classroom + Membership entities — `Classroom` extends `BaseEntity`; `ClassroomMembershipId` (`@Embeddable`, composite PK); `ClassroomMembership` (`@EmbeddedId`, `@MapsId`, `@CreatedDate` joinedAt, isActive) | `classroom/Classroom.java`, `classroom/ClassroomMembershipId.java`, `classroom/ClassroomMembership.java` |
| T034 | ClassroomRepository + MembershipRepository — `findByTeacher_Id`, `findByStudentId` (JPQL join), `findByJoinCode`, `existsByJoinCode`, `existsByIdAndTeacher_Id`; Membership: `findByClassroom_Id`, `findByClassroom_IdAndStudent_Id`, `existsMembership` (isActive check) | `classroom/ClassroomRepository.java`, `classroom/MembershipRepository.java` |
| T035 | ClassroomSecurityBean — `@Component("classroomSecurity")`; `isOwner(classroomId, auth)` dùng `existsByIdAndTeacher_Id`; `isMember(classroomId, auth)` kiểm tra teacher trước rồi active membership | `classroom/ClassroomSecurityBean.java` |
| T036 | Classroom DTOs — `CreateClassroomRequest`, `UpdateClassroomRequest`, `JoinClassroomRequest`; `ClassroomDto` (nested `TeacherInfo`, `NextSchedule` nullable); `MemberDto`; `JoinResponse` record | `classroom/Create|UpdateClassroomRequest.java`, `classroom/JoinClassroomRequest.java`, `classroom/ClassroomDto.java`, `classroom/MemberDto.java`, `classroom/JoinResponse.java` |
| T037 | ClassroomService (CRUD) — `create` (generateUniqueJoinCode loop), `listForUser` (teacher vs student branch), `getById`, `update`, `archive` (soft delete) | `classroom/ClassroomService.java` |
| T038 | ClassroomService (join + members) — `join` (404 not found, 409 duplicate, re-activate kicked membership), `listMembers`, `kickMember`, `regenerateCode` | `classroom/ClassroomService.java` |
| T039 | ClassroomController — 9 endpoints: GET|POST /classrooms, GET|PUT|DELETE /{id}, POST /join, GET|DELETE /{id}/members[/{studentId}], POST /{id}/join-code/regenerate; `@PreAuthorize` isOwner/isMember/hasRole | `classroom/ClassroomController.java` |

#### M05 — Post/Feed (T040–T041)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T040 | Flyway V4: posts + attachments — DDL `posts` + `post_attachments`, composite index `(classroom_id, created_at DESC)`, FK với `ON DELETE CASCADE` | `db/migration/V4__create_posts.sql` |
| T041 | Post + PostAttachment entities — `Post` extends `BaseEntity` (ManyToOne Classroom/User lazy, OneToMany attachments cascade+orphanRemoval, `@BatchSize(20)` để tránh N+1); `PostAttachment` entity riêng (không extend BaseEntity, dùng `uploaded_at`) | `post/Post.java`, `post/PostAttachment.java` |
| T042 | PostRepository + AttachmentRepository — `findByClassroomId` JPQL paginated với JOIN FETCH author + countQuery riêng; `findByIdAndClassroomId` để load post kèm author; `findByIdAndPost_Id` trong AttachmentRepo | `post/PostRepository.java`, `post/PostAttachmentRepository.java` |
| T043 | PostService — `list` (paginated, `Map.Entry<List, PageMeta>`), `create`, `update`/`delete` (assertCanEdit: author or teacher), `addAttachments` (upload MinIO `post-attachments/{postId}/{uuid}.{ext}`, max 50MB), `deleteAttachment` (remove MinIO + DB); `PostDto` với nested `AuthorInfo` + `AttachmentDto`; `CreatePostRequest`, `UpdatePostRequest` | `post/PostService.java`, `post/PostDto.java`, `post/Create|UpdatePostRequest.java` |
| T044 | PostController — 6 endpoints: GET/POST /{classroomId}/posts, PUT/DELETE /{classroomId}/posts/{postId}, POST/DELETE /{classroomId}/posts/{postId}/attachments[/{attachmentId}]; tất cả `@PreAuthorize("@classroomSecurity.isMember(...)")`, author/teacher check trong service | `post/PostController.java` |

#### M06 — Schedule (T045–T047)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T045 | Flyway V5: schedules — DDL `schedules` + index `(classroom_id, scheduled_date)`; có `updated_at` để extend BaseEntity | `db/migration/V5__create_schedules.sql` |
| T046 | Schedule entity + ScheduleRepository — extends BaseEntity; LocalDate/LocalTime fields; `findByClassroomId`, `findByClassroomIdAndDateBetween`, `findFirstBy...GreaterThanEqual` (cho nextSchedule), `findByIdAndClassroom_Id` | `schedule/Schedule.java`, `schedule/ScheduleRepository.java` |
| T047 | ScheduleService + ScheduleController — `list` (optional from/to filter), `create`, `update` (partial update), `delete` (session check deferred to M09); 4 endpoints `[MEMBER]`/`[OWNER]`; ClassroomDto.nextSchedule và ClassroomService updated để inject ScheduleRepository | `schedule/ScheduleService.java`, `schedule/ScheduleController.java`, `schedule/ScheduleDto.java`, `schedule/Create|UpdateScheduleRequest.java`, `classroom/ClassroomDto.java`, `classroom/ClassroomService.java` |

#### M07 — Document (T048–T050)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T048 | Flyway V6: classroom_documents — DDL `classroom_documents` (id, classroom_id FK, uploader_id FK, file_name, storage_key, file_size_bytes, file_ext, uploaded_at) + `idx_documents_classroom` | `db/migration/V6__create_documents.sql` |
| T049 | ClassroomDocument entity + ClassroomDocumentRepository — không extend BaseEntity (chỉ có uploaded_at); `findByClassroomId` paginated (JOIN FETCH uploader), `findAllByClassroomId`, `findByIdAndClassroom_Id`; thêm `findByPost_ClassroomId` + `findAllByPost_ClassroomId` vào `PostAttachmentRepository` cho merge list | `document/ClassroomDocument.java`, `document/ClassroomDocumentRepository.java`, `post/PostAttachmentRepository.java` |
| T050 | DocumentService + DocumentController — `list` (source=direct/post/all; source=all merge 2 nguồn in-memory sort uploadedAt DESC + manual paginate), `upload` (multipart, MinIO `classroom-documents/{classroomId}/{uuid}.{ext}`, max 50MB), `delete` (chỉ xóa direct upload); 3 endpoints: GET/POST /{classroomId}/documents, DELETE /{classroomId}/documents/{documentId}; `[MEMBER]`/`[OWNER]` auth | `document/DocumentService.java`, `document/DocumentController.java`, `document/DocumentDto.java` |

#### M08 — Upload (T051–T052)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T051 | UploadService — validate fileSizeBytes (avatar ≤5MB, others ≤50MB), build objectKey `uploads/{year}/{month}/{uuid}-{sanitizedName}`, generate MinIO presigned PUT URL (5 min / 300s expiry), trả `PresignedUrlDto` (fileName, fileKey, uploadUrl, expiresAt) | `upload/UploadService.java`, `upload/UploadPresignRequest.java`, `upload/PresignedUrlDto.java` |
| T052 | UploadController — `POST /api/v1/uploads/presign [AUTH]`; `@Valid UploadPresignRequest` (files 1–10, purpose: post_attachment/classroom_document/avatar) | `upload/UploadController.java` |

#### M09 — Session (T053–T054)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T053 | Flyway V7: sessions + presences + summaries — DDL `sessions` (status CHECK waiting/active/ended, partial index WHERE status='active'), `session_presences` (composite PK session_id+student_id, left_at nullable), `session_student_summaries` (composite PK, NUMERIC(5,2) score_percent) | `db/migration/V7__create_sessions.sql` |
| T054 | Session + SessionPresence + SessionStudentSummary entities — `Session` không extend BaseEntity (không có updated_at), `SessionStatus` enum lowercase, `SessionPresenceId`/`SessionStudentSummaryId` embeddable composite PKs, `SessionPresence` dùng `@EmbeddedId`+`@MapsId`, `SessionStudentSummary` dùng `BigDecimal` cho scorePercent | `session/Session.java`, `session/SessionStatus.java`, `session/SessionPresence.java`, `session/SessionPresenceId.java`, `session/SessionStudentSummary.java`, `session/SessionStudentSummaryId.java` |
| T055 | SessionRepository — `findByClassroomId` paginated (JOIN FETCH teacher, ORDER BY createdAt DESC), `findActiveByClassroomId` (status=active, dùng partial index), `findTeacherIdById` (JPQL projection → Optional<UUID>), `findByIdAndClassroom_Id` | `session/SessionRepository.java` |
| T056 | SessionPresenceRepository — `findBySessionId` (JOIN FETCH student), `findById_SessionIdAndId_StudentId`, `findActiveStudentIds` (left_at IS NULL → List<UUID>), `updateLeftAt` (@Modifying JPQL UPDATE) | `session/SessionPresenceRepository.java` |
| T057 | SessionSecurityBean — `@Component("sessionSecurity")`; `isOwner(sessionId, auth)` dùng `findTeacherIdById`; `isParticipant(sessionId, auth)` kiểm tra teacher trước (role+findTeacherId) rồi student presence record | `session/SessionSecurityBean.java` |
| T058 | Session DTOs — `CreateSessionRequest` (scheduleId nullable); `SessionDto` (@JsonInclude NON_NULL, factory `forStart`+`forListItem`); `SessionDetailDto` (nested TeacherInfo, factory `from`); `PresenceDto` (studentId, name, avatarColor, joinedAt, isOnline, factory `from(presence, isOnline)`) | `session/CreateSessionRequest.java`, `session/SessionDto.java`, `session/SessionDetailDto.java`, `session/PresenceDto.java` |
| T059 | SessionService (start) — validate no active session (ConflictException `SESSION_ALREADY_ACTIVE`), load classroom + optional schedule, build Session (status=active, startedAt=now()), save, generate wsTicket → `SessionDto.forStart`; thêm `existsBySchedule_Id` vào SessionRepository + fix `ScheduleService.delete` M06 TODO | `session/SessionService.java`, `session/SessionRepository.java`, `schedule/ScheduleService.java` |
| T060 | SessionService (join/leave/presence) — `join`: upsert presence (clear leftAt nếu re-join), generate wsTicket → `JoinSessionResponse` record; `leave`: updateLeftAt; `getPresence`: findBySessionId + findActiveStudentIds → Set để tính isOnline | `session/SessionService.java`, `session/JoinSessionResponse.java`, `session/SessionPresenceRepository.java` |
| T061 | SessionService (end) — validate not already ended, set status=ended + endedAt=now, compute duration (Duration.between), countById_SessionId → `SessionEndResponse` record; questionCount=0 (wired M10); TODO async summary M14 | `session/SessionService.java`, `session/SessionEndResponse.java` |
| T062 | SessionController — 7 endpoints: POST /classrooms/{id}/sessions [OWNER], GET /classrooms/{id}/sessions [MEMBER], GET /sessions/{id} [AUTH], POST /sessions/{id}/end [sessionSecurity.isOwner], POST /sessions/{id}/join [STUDENT], POST /sessions/{id}/leave [STUDENT], GET /sessions/{id}/presence [sessionSecurity.isParticipant]; class-level @RequestMapping("/api/v1") | `session/SessionController.java` |

#### M10 — Question (T063–T064)

| Task | Mô tả | File(s) |
|------|-------|---------|
| T063 | Flyway V8: questions + options + answers — DDL `questions` (status CHECK draft/running/ended, partial index WHERE status='running'), `question_options` (ON DELETE CASCADE), `student_answers` (UNIQUE(question_id, student_id), UUID[] selected_option_ids) | `db/migration/V8__create_questions.sql` |
| T064 | Question + QuestionOption + StudentAnswer entities — `Question` không extend BaseEntity (@CreatedDate createdAt, OneToMany options cascade+orphanRemoval, @Builder.Default status=draft), `QuestionType`/`QuestionStatus`/`ConfidenceLevel` enums; `QuestionOption` (label, text, isCorrect, optionOrder); `StudentAnswer` (`selected_option_ids UUID[]` via `@Type(UUIDArrayType.class)` từ hypersistence-utils, `ConfidenceLevel` enum nullable, `Boolean correct` nullable) | `question/Question.java`, `question/QuestionType.java`, `question/QuestionStatus.java`, `question/QuestionOption.java`, `question/StudentAnswer.java`, `question/ConfidenceLevel.java` |
| T065 | Question/Option/Answer repositories — `QuestionRepository`: `findBySessionId` (DISTINCT JOIN FETCH options, ORDER BY questionOrder), `findRunningBySessionId` (status=running dùng partial index), `findByIdAndSession_Id`, `countBySession_Id`; `QuestionOptionRepository`: `findByQuestionId`; `StudentAnswerRepository`: `findByQuestion_Id`, `findByQuestion_IdAndStudent_Id`, `existsByQuestion_IdAndStudent_Id` | `question/QuestionRepository.java`, `question/QuestionOptionRepository.java`, `question/StudentAnswerRepository.java` |
| T066 | Question DTOs — `CreateQuestionRequest` (@Valid type/content/timerSeconds/@Positive/options); `CreateOptionRequest` (label/text/isCorrect); `OptionDto` record (id, label, text, isCorrect, order); `QuestionDto` (@JsonInclude NON_NULL, @Builder, factory `from(Question)`); `QuestionStartResponse` record (id, status, startedAt, endsAt); `QuestionEndResponse` record (id, status, endedAt); `QuestionStatsDto` record với nested `OptionDistribution`, `ConfidenceBreakdown`, `SilentStudent` | `question/Create{Question,Option}Request.java`, `question/OptionDto.java`, `question/QuestionDto.java`, `question/Question{Start,End}Response.java`, `question/QuestionStatsDto.java` |
| T067 | QuestionTimerService — `ScheduledExecutorService` 4 threads, `ConcurrentHashMap<UUID, ScheduledFuture<?>>` activeTimers; `startTimer(questionId, seconds, sessionId)`, `cancelTimer(questionId)`; `autoEndQuestion` dùng `TransactionTemplate` (không dùng `@Transactional` vì gọi từ timer thread ngoài proxy); R02 recovery: `@EventListener(ApplicationStartedEvent)` → reschedule remaining / auto-end expired; `QuestionRepository.findAllRunning()` thêm JOIN FETCH session | `question/QuestionTimerService.java`, `question/QuestionRepository.java` (thêm `findAllRunning`) |
| T068 | QuestionService (CRUD + start) — `list` (findBySessionId → QuestionDto), `create` (validate options cho MCQ, auto-increment order, cascade save options), `start` (validate session active + no running question, set running+startedAt+endsAt, Redis SET `active_question` TTL 5min, startTimer nếu có timerSeconds); broadcast TODO M13 | `question/QuestionService.java` |
| T069 | QuestionService (end + stats) — `end` (validate running, set ended+endedAt, cancelTimer, Redis DELETE active_question); `getStats` (answers JOIN FETCH student, optionDistribution từ selectedOptionIds UUID[], confidenceBreakdown groupingBy, silentStudents = activePresence − answered); broadcast TODO M13 | `question/QuestionService.java`, `question/StudentAnswerRepository.java` (thêm JOIN FETCH) |
| T070 | SilentStudentDetector — `@Scheduled(fixedDelay=10_000)`: loop `findActiveIds()`, get `active_question` từ Redis, compute `silent = presence − answered`, log.debug + broadcast TODO M13; thêm `findActiveIds()` vào `SessionRepository`; `@EnableScheduling` thêm vào `ClasspulseApplication` | `question/SilentStudentDetector.java`, `session/SessionRepository.java`, `ClasspulseApplication.java` |
| T071 | QuestionController — 5 endpoints: GET /questions [AUTH], POST /questions [sessionSecurity.isOwner], POST /questions/{qid}/start [OWNER], POST /questions/{qid}/end [OWNER], GET /questions/{qid}/stats [OWNER]; class-level `@RequestMapping("/api/v1/sessions/{sessionId}")` | `question/QuestionController.java` |

### In Progress

_(none)_

### Next

T072 — StudentAnswerService (submit)
