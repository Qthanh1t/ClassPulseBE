# ClassPulse — Implementation Plan

> **Project:** ClassPulse / StudyQuest (đồ án tốt nghiệp)  
> **Stack:** Java 21 · Spring Boot 3.x · PostgreSQL 16 · Redis 7 · WebSocket/STOMP · WebRTC  
> **Architecture:** Modular Monolith  
> **Generated:** 2026-04-26

---

# Modules

## Module Overview

| # | Module | Responsibility | Tables | Endpoints |
|---|--------|---------------|--------|-----------|
| M01 | **Infrastructure** | Project setup, common layer, cross-cutting concerns | — | — |
| M02 | **Auth** | JWT login/register/refresh/logout, WS ticket | `users`, `refresh_tokens` | 4 REST |
| M03 | **User** | Profile management, avatar upload | `users` | 5 REST |
| M04 | **Classroom** | Lớp học CRUD, join by code, members | `classrooms`, `classroom_memberships` | 9 REST |
| M05 | **Post/Feed** | Bảng tin rich-text, attachments | `posts`, `post_attachments` | 5 REST |
| M06 | **Schedule** | Lịch học CRUD | `schedules` | 4 REST |
| M07 | **Document** | Kho tài liệu classroom | `classroom_documents` | 3 REST |
| M08 | **Upload** | MinIO presigned URL generation | — | 1 REST |
| M09 | **Session** | Vòng đời buổi học, presence, wsTicket | `sessions`, `session_presences` | 7 REST |
| M10 | **Question** | Q&A lifecycle, timer, stats | `questions`, `question_options` | 5 REST |
| M11 | **Student Answer** | Submit + view answers | `student_answers` | 2 REST |
| M12 | **Breakout** | Breakout rooms, teacher join/broadcast | `breakout_sessions`, `breakout_rooms`, `breakout_assignments` | 6 REST |
| M13 | **Realtime/WebSocket** | STOMP broker, presence, chat, raise hand, focus, WebRTC signaling | `chat_messages`, `raised_hands` | ∞ WS events |
| M14 | **Dashboard** | Teacher analytics sau session | `session_student_summaries` | 1 REST |
| M15 | **Student Review** | Student xem lại kết quả cá nhân | `session_student_summaries` | 1 REST |
| M16 | **Admin** | Quản lý user + system stats | — | 3 REST |

---

## Module Detail

### M01 — Infrastructure / Common

**Responsibility:** Nền tảng kỹ thuật dùng chung cho toàn bộ ứng dụng.

**Sub-features:**
- Spring Boot project init (Gradle Kotlin DSL, Java 21, dependencies)
- Docker Compose dev environment (Postgres, Redis, MinIO, Coturn)
- `application.yml` / `application-dev.yml` / `application-prod.yml`
- Flyway migration framework
- `BaseEntity` (UUID PK, createdAt, updatedAt via JPA Auditing)
- `ApiResponse<T>` response wrapper + `PageMeta`
- Exception hierarchy: `AppException`, `NotFoundException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `BusinessException`
- `GlobalExceptionHandler` (@RestControllerAdvice)
- `RequestLoggingFilter` (MDC requestId)
- Utility: `JoinCodeGenerator`, `SlugUtils`
- OpenAPI / Swagger config

---

### M02 — Auth

**Responsibility:** Xác thực danh tính người dùng. Phát hành JWT access token (15 phút) + refresh token httpOnly cookie (30 ngày). Phát hành WS ticket cho WebSocket.

**Sub-features:**
- Register (teacher / student) — admin không tự tạo được
- Login với bcrypt verify
- Refresh token rotation (revoke cũ, cấp mới)
- Logout (revoke refresh token, xóa cookie)
- `JwtTokenProvider` (generate + validate + parse claims)
- `JwtAuthFilter` (OncePerRequestFilter)
- `SecurityConfig` (filter chain, CORS, stateless)
- `JwtAuthEntryPoint` + `JwtAccessDeniedHandler` (JSON 401/403)
- `WsTicketService` (Redis one-time ticket, TTL 60s)
- `RefreshTokenService` (store hash, rotate on use)

---

### M03 — User

**Responsibility:** Quản lý profile cá nhân.

**Sub-features:**
- GET /users/me (kèm role-specific stats)
- PUT /users/me (tên, avatarColor)
- POST /users/me/avatar (multipart, max 5MB)
- GET /users (admin only, paginated)
- PUT /users/:userId (admin: đổi role, ban/unban)

---

### M04 — Classroom

**Responsibility:** Teacher tạo lớp học, student tham gia bằng mã code.

**Sub-features:**
- Tạo lớp (auto-generate joinCode)
- Lấy danh sách lớp theo user (teacher own / student member)
- Chi tiết lớp (chỉ member/owner xem)
- Cập nhật lớp, soft delete (archive)
- Student join bằng joinCode
- Danh sách thành viên
- Kick student
- Regenerate joinCode
- `ClassroomSecurityBean` (isOwner, isMember)

---

### M05 — Post/Feed

**Responsibility:** Bảng tin lớp học, hỗ trợ rich text + đính kèm file.

**Sub-features:**
- Danh sách post (paginated, newest first)
- Tạo post (rich text HTML)
- Sửa / xóa post (tác giả hoặc teacher)
- Upload file attachment vào post (multipart, nhiều file)
- Xóa attachment

---

### M06 — Schedule

**Responsibility:** Lịch học của lớp.

**Sub-features:**
- Danh sách lịch (filter by date range)
- Thêm / sửa / xóa lịch
- Link lịch với Session record sau khi diễn ra
- Không cho xóa lịch đã có session

---

### M07 — Document

**Responsibility:** Kho tài liệu lớp học (gộp từ post attachments + direct upload).

**Sub-features:**
- Lấy tất cả tài liệu (query gộp 2 nguồn)
- Upload trực tiếp (không qua bài đăng)
- Xóa direct upload

---

### M08 — Upload

**Responsibility:** MinIO presigned URL để client upload thẳng lên object storage, không đi qua Spring server.

**Sub-features:**
- Nhận danh sách file cần upload + purpose (`post_attachment`, `classroom_document`, `avatar`)
- Generate presigned PUT URL cho từng file
- Trả về fileKey để client reference sau khi upload

---

### M09 — Session

**Responsibility:** Vòng đời buổi học (waiting → active → ended). Presence tracking.

**Sub-features:**
- Teacher bắt đầu session (tạo record status=active, trả wsTicket)
- Teacher kết thúc session (async compute summaries)
- Student join session (upsert session_presence, trả wsTicket)
- Student leave session (set left_at)
- Lấy presence list (ai đang online)
- Lịch sử session của lớp
- Chi tiết session
- `SessionSecurityBean` (isOwner, isParticipant)

---

### M10 — Question

**Responsibility:** Teacher tạo và điều khiển câu hỏi trong session. Timer server-side authoritative.

**Sub-features:**
- Danh sách câu hỏi của session
- Tạo câu hỏi (single / multiple / essay + options + timer)
- Start câu hỏi (draft → running, set endsAt, broadcast, start server timer)
- End câu hỏi sớm (running → ended, cancel timer, broadcast stats)
- Live stats (answeredCount, correctCount, confidence, silentStudents)
- `QuestionTimerService` (ScheduledExecutorService, auto-end khi timer hết)
- `SilentStudentDetector` (@Scheduled mỗi 10s)

---

### M11 — Student Answer

**Responsibility:** Student nộp đáp án (chỉ 1 lần). Server tính isCorrect cho MCQ.

**Sub-features:**
- Submit answer (validate question running, validate options belong to question, UNIQUE constraint)
- View answers (teacher: tất cả; student: chỉ bản thân)
- Broadcast `answer_aggregate` đến teacher sau khi submit

---

### M12 — Breakout

**Responsibility:** Teacher chia nhóm breakout, gán task, broadcast, vào/rời phòng.

**Sub-features:**
- Tạo breakout session + phòng + gán student (transaction)
- Broadcast `breakout_started` đến tất cả
- Lấy active breakout
- End breakout → broadcast `breakout_ended`
- Teacher join room → broadcast `teacher_joined_room` đến phòng đó
- Teacher leave room
- Broadcast message đến tất cả phòng

---

### M13 — Realtime / WebSocket

**Responsibility:** Kênh sự kiện realtime — STOMP over WebSocket. Bao gồm chat, raise hand, focus, presence, WebRTC signaling.

**Sub-features:**
- `WebSocketConfig` (STOMP + SockJS + Redis relay)
- `JwtHandshakeHandler` (validate wsTicket tại handshake)
- `JwtChannelInterceptor` (validate tại mỗi inbound message)
- `SessionBroadcastService` (broadcast to topic / user)
- Presence tracking (SessionConnectedEvent / SessionDisconnectEvent → Redis SET)
- Chat: `ChatWsController` (nhận `chat_send`) + `ChatController` (load history) + `ChatRepository`
- Raise hand: `RaiseHandWsController` (nhận `raise_hand`) → update Redis SET + broadcast
- Focus student: `FocusWsController` (nhận `focus_student`) → broadcast `focus_changed`
- WebRTC signaling: `WebRtcSignalingController` (forward offer/answer/ICE)
- Heartbeat: no-op handler để giữ connection

---

### M14 — Dashboard

**Responsibility:** Teacher xem analytics sau buổi học.

**Sub-features:**
- `SessionSummaryComputeJob` (async sau session end)
- GET /sessions/:id/dashboard (per-question stats + per-student scores)

---

### M15 — Student Review

**Responsibility:** Student xem lại kết quả cá nhân.

**Sub-features:**
- GET /sessions/:id/review (per-question: myAnswer, correctAnswer, confidence, result)
- Chỉ xem được session của lớp mình đang học

---

### M16 — Admin

**Responsibility:** Quản trị hệ thống.

**Sub-features:**
- GET /admin/stats (tổng số user, classroom, session active)
- GET /admin/classrooms (tất cả lớp)
- PUT /users/:userId (ban/unban, đổi role) — dùng chung endpoint với M03

---

# Tasks

> Format: **[ID] Task name** | Input → Output | API/Table | Difficulty (E=easy, M=medium, H=hard) | Est.

## M01 — Infrastructure

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T001 | Init Gradle project | Spring Initializr: Java 21, Spring Web, Security, Data JPA, WebSocket, Redis, Flyway, Actuator, Lombok, Validation, Springdoc — dùng Gradle Kotlin DSL | — → `build.gradle.kts`, `settings.gradle.kts`, project skeleton | E · 1h |
| T002 | Docker Compose dev | Postgres 16, Redis 7, MinIO, Coturn với volumes và ports | `.env.example` → `docker-compose.yml` | E · 1h |
| T003 | application.yml | datasource, redis, jwt, minio, flyway, server config cho dev + prod profiles | env vars → `application.yml` | E · 1h |
| T004 | BaseEntity | `@MappedSuperclass` với UUID PK (gen_random_uuid), createdAt, updatedAt via JPA Auditing | — → `BaseEntity.java` | E · 0.5h |
| T005 | ApiResponse wrapper | Generic `ApiResponse<T>` với success/data/meta/error; `PageMeta`; static factory methods | — → `ApiResponse.java` | E · 1h |
| T006 | Exception hierarchy | `AppException` (abstract), `NotFoundException`, `ConflictException`, `ForbiddenException`, `UnauthorizedException`, `BusinessException` | — → 6 exception classes | E · 1h |
| T007 | GlobalExceptionHandler | `@RestControllerAdvice` xử lý `AppException`, `MethodArgumentNotValidException`, `Exception` | exception → `ApiResponse` JSON | E · 1h |
| T008 | RequestLoggingFilter | MDC requestId + log HTTP method/path/status/duration | request → log + X-Request-ID header | E · 1h |
| T009 | Utility classes | `JoinCodeGenerator` (random 6-8 chars uppercase alphanumeric) | — → `JoinCodeGenerator.java` | E · 0.5h |
| T010 | OpenAPI config | Springdoc với Bearer auth scheme, contact info | — → `OpenApiConfig.java` + Swagger UI tại `/swagger-ui.html` | E · 0.5h |

## M02 — Auth

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T011 | Flyway V1: users | DDL cho `users` table + indexes | — → `V1__create_users.sql` | E · 0.5h |
| T012 | Flyway V2: refresh_tokens | DDL cho `refresh_tokens` table + indexes | — → `V2__create_refresh_tokens.sql` | E · 0.5h |
| T013 | User entity + Role enum | JPA entity cho `users`, `Role` enum (STUDENT/TEACHER/ADMIN) | DB table → `User.java`, `Role.java` | E · 1h |
| T014 | UserRepository | `JpaRepository<User, UUID>` + `findByEmail`, `existsByEmail` | — → `UserRepository.java` | E · 0.5h |
| T015 | JwtTokenProvider | Generate access token (JJWT, HS512), validate signature, parse claims (`sub`=userId, `role`, `name`, `jti`) | userId+role → JWT string; JWT string → Claims | M · 2h |
| T016 | JwtAuthFilter | `OncePerRequestFilter`: extract Bearer token → validate → set `SecurityContextHolder` | HTTP request → authenticated SecurityContext | M · 1.5h |
| T017 | SecurityConfig | `SecurityFilterChain`: stateless, CSRF off, CORS, public routes, addFilterBefore JwtAuthFilter, `PasswordEncoder` bean | — → `SecurityConfig.java` | M · 2h |
| T018 | JwtAuthEntryPoint + AccessDeniedHandler | Trả JSON 401/403 thay vì HTML mặc định | auth exception → `{success:false,error:{...}}` JSON | E · 1h |
| T019 | Auth DTOs | `RegisterRequest` (@Valid: email, password, name, role), `LoginRequest`, `AuthResponse` | — → 3 DTO classes | E · 1h |
| T020 | RefreshToken entity + repo | JPA entity, `RefreshTokenRepository` với `findByTokenHashAndRevokedFalse` | — → entity + repo | E · 1h |
| T021 | RefreshTokenService | `createRefreshToken` (store bcrypt hash), `validateAndConsume` (rotate), `revokeAllForUser` | userId → rawToken; rawToken → userId | M · 2h |
| T022 | WsTicketService | Redis SET với 60s TTL, one-time GET+DELETE | userId → ticket string; ticket → userId Optional | M · 1.5h |
| T023 | AuthService | `register` (validate uniqueness, hash pw, save user, issue tokens), `login` (verify pw, issue tokens) | RegisterRequest/LoginRequest → AuthResponse + cookie | M · 2h |
| T024 | AuthController | 4 endpoints: POST /register, /login, /refresh, /logout. Set/clear httpOnly cookie. | — → `AuthController.java` | M · 2h |
| T025 | Auth integration test | JUnit 5 + Testcontainers: register → login → refresh → logout happy path | — → `AuthIntegrationTest.java` | M · 2h |

## M03 — User

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T026 | UserDto + UpdateProfileRequest | DTO với stats field (classroomsCount, sessionsCount...), update request | — → DTOs | E · 1h |
| T027 | UserService (getMe) | Load user + compute role-specific stats (count từ DB) | userId → UserDto | M · 1.5h |
| T028 | UserService (updateProfile) | Update name/avatarColor | UpdateProfileRequest + userId → UserDto | E · 0.5h |
| T029 | UserService (uploadAvatar) | Nhận multipart, validate ext/size, upload lên MinIO, update avatar_url | MultipartFile → avatarUrl | M · 1.5h |
| T030 | MinioConfig | MinioClient bean với endpoint/credentials, tạo bucket nếu chưa có | env vars → `MinioConfig.java` | M · 1h |
| T031 | UserController | 5 endpoints: GET/PUT /me, POST /me/avatar, GET/PUT /users/:id | — → `UserController.java` | E · 1.5h |

## M04 — Classroom

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T032 | Flyway V3: classrooms + memberships | DDL cho 2 tables + indexes | — → `V3__create_classrooms.sql` | E · 0.5h |
| T033 | Classroom + Membership entities | JPA entities | DB → `Classroom.java`, `ClassroomMembership.java` | E · 1h |
| T034 | ClassroomRepository + MembershipRepository | Custom queries: `findByTeacherId`, `findByStudentId`, `existsByJoinCode`, `existsByIdAndTeacherId`, `existsMembership` | — → 2 repositories | M · 1.5h |
| T035 | ClassroomSecurityBean | `isOwner(classroomId, auth)`, `isMember(classroomId, auth)` — dùng trong `@PreAuthorize` | — → `ClassroomSecurityBean.java` | M · 1h |
| T036 | Classroom DTOs | `CreateClassroomRequest`, `ClassroomDto` (kèm nextSchedule, studentCount), `JoinClassroomRequest` | — → DTOs | E · 1h |
| T037 | ClassroomService (CRUD) | create (auto-generate joinCode), listForUser (teacher vs student), getById, update, archive | requests → DTOs | M · 2.5h |
| T038 | ClassroomService (join + members) | join (validate code, 409 if duplicate), listMembers, kickMember, regenerateCode | joinCode → membership; classroomId → List<MemberDto> | M · 2h |
| T039 | ClassroomController | 9 endpoints (8 + regenerate code) với `@PreAuthorize` | — → `ClassroomController.java` | M · 2h |

## M05 — Post/Feed

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T040 | Flyway V4: posts + attachments | DDL + composite index trên (classroom_id, created_at DESC) | — → `V4__create_posts.sql` | E · 0.5h |
| T041 | Post + PostAttachment entities | JPA entities | DB → `Post.java`, `PostAttachment.java` | E · 1h |
| T042 | PostRepository + AttachmentRepository | `findByClassroomIdOrderByCreatedAtDesc` paginated | — → repositories | E · 0.5h |
| T043 | PostService | list (paginated), create, update (author check), delete (author or teacher), addAttachments (upload to MinIO + save), deleteAttachment | requests → DTOs | M · 3h |
| T044 | PostController | 5 endpoints | — → `PostController.java` | E · 1.5h |

## M06 — Schedule

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T045 | Flyway V5: schedules | DDL + index (classroom_id, scheduled_date) | — → `V5__create_schedules.sql` | E · 0.5h |
| T046 | Schedule entity + repo | JPA entity, `findByClassroomIdAndDateBetween` | — → entity + repo | E · 1h |
| T047 | ScheduleService + Controller | list (date filter), create, update, delete (422 nếu đã có session) | requests → DTOs | M · 2h |

## M07 — Document

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T048 | Flyway V6: classroom_documents | DDL + index classroom_id | — → `V6__create_documents.sql` | E · 0.5h |
| T049 | ClassroomDocument entity + repo | JPA entity, repo | — → entity + repo | E · 0.5h |
| T050 | DocumentService + Controller | list (gộp 2 nguồn: post_attachments UNION classroom_documents), upload direct, delete | requests → DTOs | M · 2h |

## M08 — Upload

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T051 | UploadService | Validate file ext/size, generate MinIO presigned PUT URL (5 min expiry), build storage key (uploads/{year}/{month}/{uuid}-{filename}) | `UploadPresignRequest` → `List<PresignedUrlDto>` | M · 2h |
| T052 | UploadController | POST /api/v1/uploads/presign | — → `UploadController.java` | E · 0.5h |

## M09 — Session

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T053 | Flyway V7: sessions + presences + summaries | DDL cho 3 tables + partial indexes | — → `V7__create_sessions.sql` | E · 1h |
| T054 | Session entity + SessionPresence entity + SessionStudentSummary entity | JPA entities | DB → 3 entity classes | E · 1h |
| T055 | SessionRepository | `findByClassroomId` paginated, `findActiveByClassroomId`, `findById`, `findTeacherId` | — → repo | M · 1h |
| T056 | SessionPresenceRepository | `findBySessionId`, upsert left_at, `findActiveStudentIds` | — → repo | M · 1h |
| T057 | SessionSecurityBean | `isOwner(sessionId, auth)`, `isParticipant(sessionId, auth)` | — → `SessionSecurityBean.java` | M · 1h |
| T058 | Session DTOs | `CreateSessionRequest`, `SessionDto`, `SessionDetailDto`, `PresenceDto` | — → DTOs | E · 1h |
| T059 | SessionService (start) | Validate no active session for classroom, create session (status=active), generate wsTicket | CreateSessionRequest + teacherId → SessionDto + wsTicket | M · 2h |
| T060 | SessionService (join/leave/presence) | Student join: upsert session_presence (joined_at), generate wsTicket. Leave: set left_at. Presence list: query DB + Redis SET | sessionId + studentId → wsTicket | M · 2h |
| T061 | SessionService (end) | status=ended, endedAt=now, async trigger summary compute | sessionId → summary | M · 1.5h |
| T062 | SessionController | 7 endpoints | — → `SessionController.java` | M · 2h |

## M10 — Question

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T063 | Flyway V8: questions + options + answers | DDL + partial indexes WHERE status='running', UNIQUE(question_id, student_id) | — → `V8__create_questions.sql` | E · 1h |
| T064 | Question + QuestionOption + StudentAnswer entities | JPA entities; `selected_option_ids UUID[]` via `@Type` PostgreSQL array | DB → 3 entities | M · 2h |
| T065 | Question/Option/Answer repositories | `findRunningBySessionId` (partial index), `findBySessionIdOrderByOrder`, option lookup by questionId | — → 3 repos | M · 1.5h |
| T066 | Question DTOs | `CreateQuestionRequest` (với nested options), `QuestionDto`, `QuestionStatsDto` | — → DTOs | E · 1.5h |
| T067 | QuestionTimerService | `ScheduledExecutorService` (4 threads), `startTimer(questionId, seconds)`, `cancelTimer`. Thread-safe `ConcurrentHashMap<UUID, ScheduledFuture>`. Auto-calls `autoEndQuestion` on expiry. | questionId + seconds → scheduled future | H · 3h |
| T068 | QuestionService (CRUD + start) | list questions, create (validate options for MCQ), start (draft→running, set endsAt, start timer, Redis SET active_question) | requests → DTOs; trigger broadcast | M · 3h |
| T069 | QuestionService (end + stats) | end (running→ended, cancel timer, compute stats, broadcast), stats query (join với session_presences cho silent detection) | questionId → stats | M · 2.5h |
| T070 | SilentStudentDetector | `@Scheduled(fixedDelay=10_000)`: loop active sessions → get active_question từ Redis → compute silent set → send `silent_alert` đến teacher | Redis data → WS event | M · 2h |
| T071 | QuestionController | 5 endpoints | — → `QuestionController.java` | M · 1.5h |

## M11 — Student Answer

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T072 | StudentAnswerService (submit) | Validate: question.status=running, selectedOptionIds ⊆ question options, UNIQUE constraint. Compute isCorrect (MCQ). Save. Broadcast `answer_aggregate` to teacher. Redis: add studentId to `session:{id}:question:{qid}:answered` | `SubmitAnswerRequest` + studentId → `StudentAnswerDto` | H · 3h |
| T073 | StudentAnswerService (view) | Teacher view: all answers for question. Student view: only own answer | questionId + auth → List | E · 1h |
| T074 | StudentAnswerController | 2 endpoints | — → `StudentAnswerController.java` | E · 1h |

## M12 — Breakout

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T075 | Flyway V9: breakout tables | DDL cho 3 tables + indexes | — → `V9__create_breakout.sql` | E · 0.5h |
| T076 | Breakout entities + repositories | BreakoutSession, BreakoutRoom, BreakoutAssignment + 3 repos | DB → entities + repos | E · 1.5h |
| T077 | BreakoutService (create) | `@Transactional`: create BreakoutSession + N rooms + assignments. Post-transaction: broadcast `breakout_started` | `CreateBreakoutRequest` → BreakoutSessionDto | M · 3h |
| T078 | BreakoutService (end + broadcast + room ops) | end: set endedAt, broadcast `breakout_ended`. broadcast: fan-out `broadcast_message`. join/leave room: broadcast `teacher_joined_room`/`teacher_left_room` | various → void | M · 2.5h |
| T079 | BreakoutController | 6 endpoints | — → `BreakoutController.java` | M · 2h |

## M13 — Realtime / WebSocket

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T080 | WebSocketConfig | STOMP endpoints `/ws`, SockJS fallback, Redis STOMP broker relay (port 61613), application destination prefix `/app`, user destination prefix `/user` | — → `WebSocketConfig.java` | H · 2.5h |
| T081 | JwtHandshakeHandler | `DefaultHandshakeHandler`: extract `ticket` từ query param, `consumeTicket` từ Redis, load user, set Principal | wsTicket → authenticated Principal | H · 2h |
| T082 | JwtChannelInterceptor | `ChannelInterceptor`: validate Principal tại mỗi STOMP SEND message | STOMP message → validated or rejected | M · 1.5h |
| T083 | SessionBroadcastService | `SimpMessagingTemplate` wrapper: `broadcastToSession`, `sendToTeacher`, `broadcastToRoom` | event object + destination → STOMP message | M · 1.5h |
| T084 | Presence tracking | `@EventListener(SessionConnectedEvent)` / `SessionDisconnectEvent`: add/remove từ Redis SET `session:{id}:presence`, broadcast `student_presence` | WS lifecycle events → Redis + WS broadcast | H · 2h |
| T085 | Flyway V10: chat_messages + raised_hands | DDL + indexes | — → `V10__create_chat_raised.sql` | E · 0.5h |
| T086 | Chat: entity + repo | `ChatMessage` entity, `ChatRepository` với cursor-based pagination | — → entity + repo | E · 1h |
| T087 | Chat: WS controller + REST history | `ChatWsController` nhận `chat_send`: validate sender in session, save to DB, broadcast `chat_message`. `ChatController` GET /sessions/:id/chat (cursor pagination) | WS message → broadcast + persist; REST → history | M · 2.5h |
| T088 | Raise hand: WS controller | `RaiseHandController` nhận `raise_hand {raised: boolean}`: add/remove từ Redis SET `session:{id}:raised_hands`, save to `raised_hands` table (log), broadcast `raise_hand_changed` | WS event → Redis + DB + broadcast | M · 2h |
| T089 | Focus student: WS controller | `FocusWsController` nhận `focus_student {studentId}`: teacher only, broadcast `focus_changed` | WS event → broadcast | E · 1h |
| T090 | WebRTC Signaling controller | `WebRtcSignalingController`: nhận `webrtc_offer/answer/ice_candidate`, forward `convertAndSendToUser` đến targetId | WS event → unicast forward | M · 2h |
| T091 | Heartbeat handler | Nhận `heartbeat` event, no-op (giữ connection alive logic phía client) | WS heartbeat → no-op | E · 0.5h |

## M14 — Dashboard

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T092 | SessionSummaryComputeJob | Async (`@Async`) sau session end: query `student_answers` + `session_presences` → compute `session_student_summaries` (INSERT INTO ... ON CONFLICT DO UPDATE) | sessionId → populated summary table | M · 2.5h |
| T093 | DashboardService | Query `session_student_summaries` + `questions` + `student_answers` để build `DashboardResponse` (overall stats + per-question stats + per-student results) | sessionId → DashboardResponse | M · 3h |
| T094 | DashboardController | GET /sessions/:id/dashboard (teacher owner only) | — → `DashboardController.java` | E · 1h |

## M15 — Student Review

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T095 | StudentReviewService | Query questions + student_answers + options cho session, build per-question result (myAnswer, correctAnswer, confidence, result enum) | sessionId + studentId → ReviewResponse | M · 2h |
| T096 | StudentReviewController | GET /sessions/:id/review (student only, must be member) | — → `StudentReviewController.java` | E · 1h |

## M16 — Admin

| ID | Task | Description | Input/Output | Difficulty |
|----|------|-------------|--------------|------------|
| T097 | AdminService | `getStats` (count users/classrooms/sessions từ DB), `listClassrooms` (paginated, filterable) | — → stats DTO + classroom list | E · 2h |
| T098 | AdminController | GET /admin/stats, GET /admin/classrooms (+ user endpoints dùng chung UserController) | — → `AdminController.java` | E · 1h |

---

# Dependencies

## Dependency Graph (Blocking Order)

```
[Foundation M01] T001→T010
    ↓
[Auth M02] T011→T025 (cần M01 hoàn chỉnh)
    ↓
[User M03] T026→T031 (cần M02: UserRepository, SecurityConfig)
    ↓
[Classroom M04] T032→T039 (cần M03: User entity)
    ↓
┌──────────────────────────────────────┐
│ CÓ THỂ PARALLEL sau Classroom:       │
│  [Post M05] T040→T044                │
│  [Schedule M06] T045→T047            │
│  [Document M07] T048→T050            │
│  [Upload M08] T051→T052              │
└──────────────────────────────────────┘
    ↓
[Session M09] T053→T062 (cần Classroom entity)
    ↓
[Question M10] T063→T071 (cần Session entity)
    ↓
[Student Answer M11] T072→T074 (cần Question entity + QuestionTimerService)
    ↓
[WebSocket M13 — Infrastructure] T080→T084 (cần Session, JwtTokenProvider)
    ↓
┌──────────────────────────────────────┐
│ CÓ THỂ PARALLEL sau WS infra:        │
│  [Chat T085→T087]                    │
│  [Raise Hand T088]                   │
│  [Focus T089]                        │
│  [WebRTC T090]                       │
│  [Breakout M12] T075→T079            │
└──────────────────────────────────────┘
    ↓
[Dashboard M14] T092→T094 (cần Session end + Question + Answer data)
[Review M15] T095→T096 (cần Question + StudentAnswer + Summary)
    ↓
[Admin M16] T097→T098 (cần tất cả entities tồn tại)
```

## Critical Path (Blocking Tasks)

Những task nếu block thì toàn bộ pipeline bị đình trệ:

| Task | Lý do critical |
|------|---------------|
| T001 (project init) | Không có thì không build được gì |
| T002 (Docker Compose) | Dev không chạy được local |
| T015 (JwtTokenProvider) | Tất cả protected endpoints phụ thuộc |
| T017 (SecurityConfig) | Filter chain không có → không auth được |
| T059 (Session start) | WS ticket không có → WebSocket không connect được |
| T067 (QuestionTimerService) | Timer sai → câu hỏi không auto-end → core feature broken |
| T080 (WebSocketConfig) | Không có thì toàn bộ realtime broken |
| T081 (JwtHandshakeHandler) | WebSocket không auth được |

## Parallelizable Task Groups

| Group | Tasks | After |
|-------|-------|-------|
| **CRUD modules** | Post (T040-T044), Schedule (T045-T047), Document (T048-T050), Upload (T051-T052) | Classroom done |
| **WS features** | Chat (T085-T087), Raise Hand (T088), Focus (T089), WebRTC (T090) | T080-T084 done |
| **Post-session analytics** | Dashboard (T092-T094), Review (T095-T096) | Session end + summaries done |

---

# Phases

## Phase 1 — Foundation & Auth

**Goal:** App chạy được với Docker. Auth flow hoàn chỉnh. Developer có thể test API bằng Swagger.

**Duration:** 2 tuần

**Tasks:** T001 → T025 (M01 + M02 đầy đủ) + T026-T031 (M03 User)

**Deliverables:**
- Spring Boot app khởi động, connect Postgres + Redis thành công
- Docker Compose chạy toàn bộ infrastructure
- Flyway migrations V1-V2 applied
- POST /auth/register, /login, /refresh, /logout hoạt động đúng
- GET /users/me trả profile
- Swagger UI có thể test với Bearer token
- `AuthIntegrationTest` pass

**Definition of Done:**
```
curl -X POST http://localhost:8080/api/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"teacher@test.com","password":"P@ss123","name":"Test Teacher","role":"teacher"}'
→ 201 với accessToken

curl -H "Authorization: Bearer {token}" http://localhost:8080/api/v1/users/me
→ 200 với user profile
```

---

## Phase 2 — Classroom CRUD

**Goal:** Teacher có thể tạo lớp. Student có thể join. Bảng tin, lịch học, tài liệu hoạt động. File upload có presigned URL.

**Duration:** 2 tuần

**Tasks:** T032→T052 (M04, M05, M06, M07, M08)

**Deliverables:**
- Flyway migrations V3-V6 applied
- 9 classroom endpoints (tạo, xem, sửa, xóa, join, members, kick, regenerate code)
- 5 post endpoints + attachment upload
- 4 schedule endpoints
- 3 document endpoints
- 1 upload presigned URL endpoint
- MinIO bucket configured, files uploadable

---

## Phase 3 — Session Core + Q&A

**Goal:** Teacher có thể bắt đầu/kết thúc buổi học. Student có thể tham gia. Q&A lifecycle hoàn chỉnh với server-side timer.

**Duration:** 2 tuần

**Tasks:** T053→T074 (M09, M10, M11)

**Deliverables:**
- Flyway migrations V7-V8 applied
- 7 session endpoints (start, end, join, leave, presence, list, get)
- 5 question endpoints (list, create, start, end, stats)
- 2 student answer endpoints (submit, view)
- `QuestionTimerService` auto-end câu hỏi sau N giây
- `SilentStudentDetector` chạy background

---

## Phase 4 — WebSocket & Realtime Core

**Goal:** WebSocket infrastructure chạy. Chat, raise hand, presence tracking, focus hoạt động realtime.

**Duration:** 2 tuần

**Tasks:** T080→T091 (M13 — WebSocket infrastructure + chat + raise hand + focus + WebRTC signaling)

**Deliverables:**
- STOMP WebSocket endpoint `/ws` với SockJS fallback
- WS ticket authentication hoạt động
- Presence tracking qua Redis (join/leave broadcast)
- Chat realtime + history load
- Raise hand realtime
- Focus student broadcast
- WebRTC signaling (offer/answer/ICE forward)
- `silent_alert` gửi đến teacher sau 10s

---

## Phase 5 — Breakout + Analytics

**Goal:** Breakout rooms hoàn chỉnh. Dashboard teacher xem được. Student review hoạt động.

**Duration:** 2 tuần

**Tasks:** T075→T079 (M12) + T092→T098 (M14, M15, M16)

**Deliverables:**
- Flyway migration V9-V10 applied
- 6 breakout endpoints
- `breakout_started` / `breakout_ended` / `broadcast_message` / `teacher_joined_room` events
- Dashboard endpoint với full analytics
- Student review endpoint
- `SessionSummaryComputeJob` chạy sau session end
- Admin stats + classrooms endpoints

---

## Phase 6 — Testing & Hardening

**Goal:** Hệ thống ổn định, có test coverage cho core flows, security hardening.

**Duration:** 1-2 tuần

**Tasks:** Testing suite + bug fixes

**Deliverables:**
- Unit tests: `AuthService`, `QuestionService`, `StudentAnswerService` (isCorrect logic)
- Integration tests: Auth flow, Session + Q&A flow, Breakout flow
- Manual test checklist hoàn thành
- Security checklist pass (xem M07 Best Practices)
- Docker Compose production config
- Performance validation (p95 < 300ms cho REST)

---

# Sprint Plan

## Sprint 1 (Week 1-2) — Foundation

**Goal:** Infrastructure chạy. Auth hoàn chỉnh.

| Task | Estimate |
|------|---------|
| T001 Init Gradle project | 1h |
| T002 Docker Compose | 1h |
| T003 application.yml | 1h |
| T004 BaseEntity | 0.5h |
| T005 ApiResponse wrapper | 1h |
| T006 Exception hierarchy | 1h |
| T007 GlobalExceptionHandler | 1h |
| T008 RequestLoggingFilter | 1h |
| T009 JoinCodeGenerator | 0.5h |
| T010 OpenAPI config | 0.5h |
| T011 Flyway V1: users | 0.5h |
| T012 Flyway V2: refresh_tokens | 0.5h |
| T013 User entity + Role enum | 1h |
| T014 UserRepository | 0.5h |
| T015 JwtTokenProvider | 2h |
| T016 JwtAuthFilter | 1.5h |
| T017 SecurityConfig | 2h |
| T018 JwtAuthEntryPoint | 1h |
| T019 Auth DTOs | 1h |
| T020 RefreshToken entity + repo | 1h |
| T021 RefreshTokenService | 2h |
| T022 WsTicketService | 1.5h |
| T023 AuthService | 2h |
| T024 AuthController | 2h |
| T025 Auth integration test | 2h |

**Total:** ~28h | **Expected Output:** Auth flow end-to-end working

---

## Sprint 2 (Week 3-4) — User + Classroom

**Goal:** User profile. Teacher tạo lớp. Student join lớp. Members management.

| Task | Estimate |
|------|---------|
| T026 UserDto + DTOs | 1h |
| T027 UserService (getMe) | 1.5h |
| T028 UserService (updateProfile) | 0.5h |
| T029 UserService (uploadAvatar) | 1.5h |
| T030 MinioConfig | 1h |
| T031 UserController | 1.5h |
| T032 Flyway V3: classrooms | 0.5h |
| T033 Classroom entities | 1h |
| T034 Classroom repositories | 1.5h |
| T035 ClassroomSecurityBean | 1h |
| T036 Classroom DTOs | 1h |
| T037 ClassroomService (CRUD) | 2.5h |
| T038 ClassroomService (join/members) | 2h |
| T039 ClassroomController | 2h |

**Total:** ~19h | **Expected Output:** Full classroom management API

---

## Sprint 3 (Week 5-6) — Content Modules

**Goal:** Bảng tin, lịch học, tài liệu, file upload presigned URL.

| Task | Estimate |
|------|---------|
| T040 Flyway V4: posts | 0.5h |
| T041 Post entities | 1h |
| T042 Post repositories | 0.5h |
| T043 PostService | 3h |
| T044 PostController | 1.5h |
| T045 Flyway V5: schedules | 0.5h |
| T046 Schedule entity + repo | 1h |
| T047 ScheduleService + Controller | 2h |
| T048 Flyway V6: documents | 0.5h |
| T049 Document entity + repo | 0.5h |
| T050 DocumentService + Controller | 2h |
| T051 UploadService (presigned) | 2h |
| T052 UploadController | 0.5h |

**Total:** ~16h | **Expected Output:** Content CRUD + file upload working

---

## Sprint 4 (Week 7-8) — Session + Q&A

**Goal:** Session lifecycle hoàn chỉnh. Q&A với server-side timer. Student submit answers.

| Task | Estimate |
|------|---------|
| T053 Flyway V7: sessions | 1h |
| T054 Session entities (3) | 1h |
| T055 SessionRepository | 1h |
| T056 SessionPresenceRepository | 1h |
| T057 SessionSecurityBean | 1h |
| T058 Session DTOs | 1h |
| T059 SessionService (start) | 2h |
| T060 SessionService (join/leave/presence) | 2h |
| T061 SessionService (end) | 1.5h |
| T062 SessionController | 2h |
| T063 Flyway V8: questions | 1h |
| T064 Question entities (3) | 2h |
| T065 Question/Option/Answer repos | 1.5h |
| T066 Question DTOs | 1.5h |
| T067 QuestionTimerService | 3h |
| T068 QuestionService (CRUD + start) | 3h |
| T069 QuestionService (end + stats) | 2.5h |
| T070 SilentStudentDetector | 2h |
| T071 QuestionController | 1.5h |
| T072 StudentAnswerService (submit) | 3h |
| T073 StudentAnswerService (view) | 1h |
| T074 StudentAnswerController | 1h |

**Total:** ~38h | **Expected Output:** Full session + Q&A backend working

---

## Sprint 5 (Week 9-10) — WebSocket Core

**Goal:** WebSocket infrastructure. Chat, presence, raise hand, focus, WebRTC signaling.

| Task | Estimate |
|------|---------|
| T080 WebSocketConfig | 2.5h |
| T081 JwtHandshakeHandler | 2h |
| T082 JwtChannelInterceptor | 1.5h |
| T083 SessionBroadcastService | 1.5h |
| T084 Presence tracking (connect/disconnect) | 2h |
| T085 Flyway V10: chat + raised_hands | 0.5h |
| T086 Chat entity + repo | 1h |
| T087 Chat WS controller + REST history | 2.5h |
| T088 Raise hand WS controller | 2h |
| T089 Focus student WS controller | 1h |
| T090 WebRTC Signaling controller | 2h |
| T091 Heartbeat handler | 0.5h |

**Total:** ~19h | **Expected Output:** Full realtime event bus working

---

## Sprint 6 (Week 11-12) — Breakout + Analytics + Admin

**Goal:** Breakout rooms. Dashboard. Student review. Admin.

| Task | Estimate |
|------|---------|
| T075 Flyway V9: breakout | 0.5h |
| T076 Breakout entities + repos | 1.5h |
| T077 BreakoutService (create) | 3h |
| T078 BreakoutService (end + ops) | 2.5h |
| T079 BreakoutController | 2h |
| T092 SessionSummaryComputeJob | 2.5h |
| T093 DashboardService | 3h |
| T094 DashboardController | 1h |
| T095 StudentReviewService | 2h |
| T096 StudentReviewController | 1h |
| T097 AdminService | 2h |
| T098 AdminController | 1h |

**Total:** ~22h | **Expected Output:** All features implemented

---

## Sprint 7 (Week 13-14) — Testing & Hardening

**Goal:** Test suite. Security audit. Bug fixes. Docker production.

| Task | Description | Estimate |
|------|-------------|---------|
| Unit: AuthService | register + login edge cases | 3h |
| Unit: QuestionTimerService | timer cancel, concurrent start | 2h |
| Unit: StudentAnswerService | isCorrect logic (single/multiple/essay) | 2h |
| Integration: Auth flow | register→login→refresh→logout | 2h |
| Integration: Session flow | start→join→question→answer→end→dashboard | 4h |
| Integration: Breakout flow | create→join→broadcast→end | 2h |
| Security audit | Checklist từ 07_Best_Practices.md | 3h |
| Docker prod config | `docker-compose.prod.yml`, Nginx reverse proxy | 3h |
| Manual test: WebSocket | Connect, chat, raise hand, focus, WebRTC signaling | 3h |
| Manual test: Q&A timer | Start question, wait timer expiry, verify auto-end | 2h |
| Performance check | Load test với k6/JMeter cho REST p95 | 2h |
| Bug fixes | Buffer | 4h |

**Total:** ~32h | **Expected Output:** Production-ready system

---

# Execution Strategy

## Backend-First Order

Implement theo thứ tự sau, validate từng bước với Swagger trước khi chuyển sang module tiếp:

```
Step 1:  M01 Infrastructure         → docker-compose up, app boots
Step 2:  M02 Auth                   → register/login/refresh working, test via Swagger
Step 3:  M03 User                   → /users/me working
Step 4:  M04 Classroom              → teacher creates classroom, student joins
Step 5:  M05 Post (parallel)        → bảng tin working
         M06 Schedule (parallel)    → lịch học working
         M07 Document (parallel)    → tài liệu working
         M08 Upload (parallel)      → presigned URL working
Step 6:  M09 Session                → session start/end/join/leave
Step 7:  M10 Question               → question CRUD + timer → AUTO-END working (critical)
Step 8:  M11 Student Answer         → submit + isCorrect computed
Step 9:  M13 WS Infrastructure      → WebSocket connects, ticket validates
Step 10: M13 Chat + Raise Hand      → realtime working in browser
Step 11: M13 WebRTC Signaling       → video connects in browser (validate with 2 tabs)
Step 12: M12 Breakout               → breakout flow end-to-end
Step 13: M14 Dashboard              → teacher sees post-session stats
Step 14: M15 Student Review         → student sees personal results
Step 15: M16 Admin                  → admin panel
Step 16: Testing sprint             → unit + integration + manual
```

### Validation Checkpoints

Sau mỗi step, không chuyển sang step tiếp nếu chưa pass:

| Step | Validation |
|------|-----------|
| 2 | POST /auth/login → 200 với valid token |
| 4 | Teacher tạo lớp, student join bằng code → 200 |
| 6 | POST /sessions → 201 với wsTicket; POST /sessions/:id/join → 200 với wsTicket |
| 7 | POST /questions/:id/start → broadcast event; timer fires auto-end sau N giây |
| 8 | POST /answers → 201 với isCorrect=true/false; 409 khi submit lần 2 |
| 9 | WS client connect với ticket → success; connect với invalid ticket → rejected |
| 10 | Gửi chat_send → nhận chat_message event phía đối diện |
| 11 | 2 browser tab: offer/answer/ICE forwarded → video stream established |
| 12 | Tạo breakout → HS nhận breakout_started event với đúng assignment |
| 13 | GET /dashboard → trả đúng per-question stats |

---

## Frontend Integration Plan

### Strategy: Mock-first, then Real API

#### Phase 1-2 Backend (Auth + Classroom)
- **Frontend:** Setup project (Vite + React + TypeScript + @stomp/stompjs + tailwind)
- **Mock:** Auth context với hardcoded user; classroom list static data
- **Connect:** Khi Auth API done → thay mock bằng real `/auth/login`, `/auth/refresh`

#### Phase 3 Backend (Session + Q&A)
- **Mock:** Session state machine với setTimeout để giả lập timer
- **Connect:** Khi Session API done → real session start/join; khi Question API done → real Q&A flow

#### Phase 4 Backend (WebSocket)
- **Mock:** Event emitter trong React để fake WS events (dùng khi test UI)
- **Connect:** Khi WS infra done → thay bằng real STOMP client (`useSessionSocket` hook)
- **Connect:** Chat, raise hand, focus, presence kết nối từng event một

#### Phase 5 Backend (Breakout + Analytics)
- **Mock:** Static breakout layout, static dashboard data
- **Connect:** Real breakout events + real dashboard/review API

### Mock API Tool
Dùng **MSW (Mock Service Worker)** hoặc **json-server** cho REST endpoints khi backend chưa ready:
```typescript
// src/mocks/handlers.ts — tắt khi connect real API
export const handlers = [
  rest.post('/api/v1/auth/login', (req, res, ctx) => {
    return res(ctx.json({ success: true, data: { accessToken: 'mock-token' } }));
  }),
];
```

---

# Testing Strategy

## Unit Tests

Dùng **JUnit 5 + Mockito**. Test service layer — mock repositories.

| Test Class | What to test | Key scenarios |
|------------|-------------|---------------|
| `AuthServiceTest` | register, login | Duplicate email → 409; Wrong password → 401; Banned user → 403 |
| `QuestionTimerServiceTest` | startTimer, cancelTimer | Timer fires after N seconds; cancelTimer prevents auto-end; concurrent start same question |
| `StudentAnswerServiceTest` | isCorrect computation | Single: 1 correct option; Multiple: all correct required; Essay: null; Duplicate submit → 409 |
| `ClassroomServiceTest` | join by code | Invalid code → 404; Already member → 409; Inactive member re-join |
| `QuestionServiceTest` | start question | Already running question → 409; Session not active → 422 |
| `SessionServiceTest` | start session | Classroom already has active session → 409 |

## Integration Tests

Dùng **Testcontainers** (real Postgres + Redis trong Docker).

| Test Class | Scenario |
|------------|---------|
| `AuthIntegrationTest` | register → login → GET /users/me → refresh → logout → verify refresh invalid |
| `ClassroomIntegrationTest` | teacher create → student join → list members → kick → verify kicked student cannot rejoin |
| `SessionQAIntegrationTest` | start session → join as student → create question → start question → submit answer → end question → verify stats → end session → verify dashboard |
| `BreakoutIntegrationTest` | start session → create breakout → verify room assignments → end breakout → verify rooms cleared |

## Manual Test Checklist

### Auth & User
- [ ] Register với email đã tồn tại → 409 với error message rõ ràng
- [ ] Login sai mật khẩu → 401
- [ ] Access protected endpoint với expired token → 401
- [ ] Refresh token hoạt động; refresh token dùng 2 lần → 401
- [ ] Upload avatar: JPG/PNG accepted; file > 5MB → 400

### Classroom
- [ ] Tạo lớp → joinCode sinh ra unique, uppercase
- [ ] Student join bằng code → appear trong members list
- [ ] Teacher kick student → student không còn trong members
- [ ] Student không thể tạo lớp (403)
- [ ] Teacher không thể join lớp (403)

### Session & Q&A
- [ ] Teacher bắt đầu session → wsTicket trả về, hết hạn sau 60s
- [ ] WS connect với expired ticket → rejected
- [ ] Tạo câu hỏi single choice → start → học sinh thấy câu hỏi ngay
- [ ] Timer 30s → câu hỏi tự end → answer_aggregate broadcast → input locked
- [ ] Submit đáp án sau khi timer hết → 422
- [ ] Submit đáp án lần 2 → 409
- [ ] Essay question: isCorrect = null
- [ ] silent_alert đến teacher sau 10s nếu có HS chưa trả lời

### WebSocket Realtime
- [ ] Chat message xuất hiện ở tất cả participants trong < 200ms
- [ ] Raise hand → teacher thấy icon ngay
- [ ] Student disconnect → student_presence "left" event đến tất cả
- [ ] Focus student → tất cả nhận focus_changed event

### Breakout
- [ ] Tạo 3 nhóm → 3 HS nhận breakout_started với đúng roomId
- [ ] Teacher join phòng 1 → chỉ HS trong phòng 1 nhận teacher_joined_room
- [ ] Broadcast message → tất cả HS (kể cả ở breakout) nhận được
- [ ] End breakout → tất cả nhận breakout_ended, trở về main room

### Dashboard & Review
- [ ] Dashboard load sau session end → stats đúng với số câu đúng/sai thực tế
- [ ] Student review: hiển thị đúng đáp án đã chọn, đáp án đúng, confidence
- [ ] Essay question result hiển thị "pending_review"

---

# Risks

## R01 — WebSocket Redis STOMP Relay Configuration

**Risk:** Spring STOMP broker relay qua Redis (port 61613) cần cấu hình đúng. Nếu sai sẽ không route được message đến đúng subscriber.

**Probability:** Cao (config phức tạp, dễ sai port/credentials)

**Mitigation:**
- Test với in-memory broker trước (không cần Redis relay), sau đó switch sang Redis relay
- Dùng `SimpleBrokerRegistration` thay vì `StompBrokerRelayRegistration` cho dev environment
- Tham khảo Spring docs: `enableStompBrokerRelay` với ActiveMQ thay vì Redis nếu gặp vấn đề

```yaml
# Dev fallback: in-memory broker (đơn giản hơn, chỉ 1 instance)
# Chỉ dùng Redis relay khi test multi-instance hoặc deploy
```

---

## R02 — QuestionTimerService — Server Restart

**Risk:** Khi server restart, `ScheduledExecutorService` mất hết timer đang chạy. Câu hỏi `status=running` sẽ bị kẹt vĩnh viễn.

**Probability:** Trung bình (chỉ xảy ra khi deploy hoặc crash)

**Mitigation:**
- Khi app start: query tất cả questions WHERE status='running' AND ends_at < now() → auto-end them
- Khi app start: query questions WHERE status='running' AND ends_at > now() → reschedule remaining timer

```java
@EventListener(ApplicationStartedEvent.class)
public void recoverActiveTimers() {
    questionRepository.findAllRunning().forEach(q -> {
        if (q.getEndsAt().isBefore(Instant.now())) {
            questionService.autoEndQuestion(q.getId());
        } else {
            long remainingMs = Duration.between(Instant.now(), q.getEndsAt()).toMillis();
            startTimerWithDelay(q.getId(), remainingMs);
        }
    });
}
```

---

## R03 — WebRTC NAT Traversal / Coturn

**Risk:** WebRTC P2P không kết nối được trong một số network environments (symmetric NAT) nếu Coturn TURN server không hoạt động đúng.

**Probability:** Trung bình (phụ thuộc vào network của user)

**Mitigation:**
- Test Coturn với `turnutils_uclient` trước khi demo
- Đảm bảo Coturn port 3478 (UDP/TCP) và 5349 (TLS) được mở trong firewall
- Implement fallback UI: hiển thị lỗi rõ ràng khi WebRTC fail, không crash toàn bộ session
- Đối với đồ án: có thể demo trên cùng network (tránh symmetric NAT)

---

## R04 — Concurrent Answer Submission Race Condition

**Risk:** 2 request submit cùng lúc từ 1 student có thể bypass UNIQUE check nếu DB chưa commit.

**Probability:** Thấp (nhưng critical nếu xảy ra)

**Mitigation:**
- UNIQUE constraint `(question_id, student_id)` ở DB level là safety net cuối cùng
- Service layer: bắt `DataIntegrityViolationException` → convert to `ConflictException` 409
- Không cần application-level lock vì DB constraint đủ mạnh

```java
try {
    studentAnswerRepository.save(answer);
} catch (DataIntegrityViolationException e) {
    throw new ConflictException("Answer already submitted for this question");
}
```

---

## R05 — Session Student Summaries Not Ready When Dashboard Loads

**Risk:** Teacher navigate to dashboard ngay sau khi end session, nhưng async compute job chưa xong → empty dashboard.

**Probability:** Cao (race condition giữa session end và async compute)

**Mitigation:**
- Option A (simple): Compute synchronously trong `endSession` (chấp nhận latency ~1-2s)
- Option B (better): Dashboard API kiểm tra `computed_at IS NULL` → trigger compute on-demand nếu chưa có
- Option C: Frontend polling (check mỗi 2s, tối đa 10s, hiển thị loading state)
- **Đề xuất cho đồ án:** Option A + loading spinner ở frontend

---

## R06 — PostgreSQL UUID Array (`selected_option_ids UUID[]`) JPA Mapping

**Risk:** JPA/Hibernate không native support `UUID[]` PostgreSQL. Cần custom UserType hoặc converter.

**Probability:** Cao (known complexity)

**Mitigation:**
- Dùng Hibernate `@Type(PostgreSQLEnumType)` với `hibernate-types` library (Vlad Mihalcea)
- Hoặc lưu dưới dạng `TEXT` với UUID list JSON serialize/deserialize
- Hoặc dùng `@Column(columnDefinition = "uuid[]")` + `@Type(value = ArrayType.class)`

```kotlin
// build.gradle.kts
implementation("com.vladmihalcea:hibernate-types-60:2.21.1")
```

---

## R07 — Scope Creep: WebRTC Video Grid

**Risk:** Implement full WebRTC video grid (UI + signaling) là phức tạp hơn nhiều so với estimate. Có thể chiếm toàn bộ Sprint 5.

**Probability:** Trung bình

**Mitigation:**
- **Ưu tiên:** Implement signaling backend trước (offer/answer/ICE forward) — đây là phần backend
- Frontend video grid: có thể dùng thư viện như `react-webrtc` hoặc `simple-peer`
- Nếu cần: placeholder UI (tên HS + avatar color box) thay vì video thật cho MVP
- WebRTC là "nice to have" — core feature là Q&A + Realtime events

---

## Summary Risk Matrix

| Risk | Probability | Impact | Priority |
|------|------------|--------|---------|
| R02 Timer recovery on restart | Trung bình | High (core feature broken) | **P1** |
| R04 Concurrent answer race | Thấp | High (data integrity) | **P1** |
| R06 UUID Array JPA mapping | Cao | Medium (can workaround) | **P2** |
| R01 Redis STOMP relay config | Cao | High (realtime broken) | **P1** |
| R05 Dashboard race condition | Cao | Low (UX, not data loss) | **P3** |
| R03 WebRTC NAT traversal | Trung bình | Medium (degraded, not broken) | **P2** |
| R07 WebRTC scope creep | Trung bình | Medium (timeline slip) | **P2** |

---

# Appendix — Flyway Migration Order

| Version | File | Tables Created |
|---------|------|---------------|
| V1 | `V1__create_users.sql` | `users` |
| V2 | `V2__create_refresh_tokens.sql` | `refresh_tokens` |
| V3 | `V3__create_classrooms.sql` | `classrooms`, `classroom_memberships` |
| V4 | `V4__create_posts.sql` | `posts`, `post_attachments` |
| V5 | `V5__create_schedules.sql` | `schedules` |
| V6 | `V6__create_documents.sql` | `classroom_documents` |
| V7 | `V7__create_sessions.sql` | `sessions`, `session_presences`, `session_student_summaries` |
| V8 | `V8__create_questions.sql` | `questions`, `question_options`, `student_answers` |
| V9 | `V9__create_breakout.sql` | `breakout_sessions`, `breakout_rooms`, `breakout_assignments` |
| V10 | `V10__create_realtime.sql` | `chat_messages`, `raised_hands` |

---

# Appendix — Redis Key Reference

| Key Pattern | Type | TTL | Purpose |
|-------------|------|-----|---------|
| `ws_ticket:{ticket}` | String | 60s | WS auth one-time token |
| `session:{id}:presence` | Set | session lifetime | studentIds online |
| `session:{id}:raised_hands` | Set | session lifetime | studentIds với hand raised |
| `session:{id}:active_question` | String | 5min | questionId đang running |
| `session:{id}:question:{qid}:answered` | Set | 5min | studentIds đã trả lời |
| `refresh_token:{hash}` | String | 30d | revocation cache |

---

*Last updated: 2026-04-26 | Author: Technical Lead*
