package com.classpulse.breakout;

import com.classpulse.auth.RefreshTokenRepository;
import com.classpulse.auth.RegisterRequest;
import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.classroom.MembershipRepository;
import com.classpulse.session.SessionRepository;
import com.classpulse.user.Role;
import com.classpulse.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class BreakoutIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @SuppressWarnings("resource")
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret",
                () -> "test-jwt-secret-key-minimum-64-chars-long-for-hs512-algorithm-ok1234");
        registry.add("app.cookie.secure", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired BreakoutSessionRepository breakoutSessionRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired ClassroomRepository classroomRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;

    private String teacherToken;
    private String s1Id, s2Id, s3Id;
    private String s1Token, s2Token, s3Token;
    private String sessionId;

    record UserInfo(String id, String token) {}

    @BeforeEach
    void setUp() throws Exception {
        // Cleanup: breakout_sessions first (no CASCADE from sessions), then sessions (CASCADE to presences)
        breakoutSessionRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        membershipRepository.deleteAllInBatch();
        classroomRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Register users
        var teacher = registerUser("teacher@test.com", "Teacher", Role.TEACHER);
        var s1 = registerUser("s1@test.com", "Student 1", Role.STUDENT);
        var s2 = registerUser("s2@test.com", "Student 2", Role.STUDENT);
        var s3 = registerUser("s3@test.com", "Student 3", Role.STUDENT);

        teacherToken = teacher.token();
        s1Id = s1.id(); s1Token = s1.token();
        s2Id = s2.id(); s2Token = s2.token();
        s3Id = s3.id(); s3Token = s3.token();

        // Create classroom
        MvcResult classRes = mockMvc.perform(post("/api/v1/classrooms")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "Test Classroom"))))
                .andExpect(status().isCreated())
                .andReturn();
        String classroomId = at(classRes, "/data/id");
        String joinCode = at(classRes, "/data/joinCode");

        // Students join classroom
        for (String token : List.of(s1Token, s2Token, s3Token)) {
            mockMvc.perform(post("/api/v1/classrooms/join")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("joinCode", joinCode))))
                    .andExpect(status().isOk());
        }

        // Start session
        MvcResult sessionRes = mockMvc.perform(
                        post("/api/v1/classrooms/{id}/sessions", classroomId)
                                .header("Authorization", "Bearer " + teacherToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isCreated())
                .andReturn();
        sessionId = at(sessionRes, "/data/id");

        // Students join session → appear in presence (leftAt = null)
        for (String token : List.of(s1Token, s2Token, s3Token)) {
            mockMvc.perform(post("/api/v1/sessions/{id}/join", sessionId)
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());
        }
    }

    // ─── T077: create ─────────────────────────────────────────────────────────

    @Test
    void create_happyPath_returns201WithRoomsAndStudents() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.breakoutSessionId").exists())
                .andExpect(jsonPath("$.data.startedAt").exists())
                .andExpect(jsonPath("$.data.rooms.length()").value(2))
                .andExpect(jsonPath("$.data.rooms[0].name").value("Group A"))
                .andExpect(jsonPath("$.data.rooms[0].order").value(1))
                .andExpect(jsonPath("$.data.rooms[0].students.length()").value(2))
                .andExpect(jsonPath("$.data.rooms[1].name").value("Group B"))
                .andExpect(jsonPath("$.data.rooms[1].order").value(2))
                .andExpect(jsonPath("$.data.rooms[1].students.length()").value(1));
    }

    @Test
    void create_emptyRooms_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("rooms", List.of()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void create_sessionNotActive_returns422() throws Exception {
        // End the session first
        mockMvc.perform(post("/api/v1/sessions/{id}/end", sessionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("SESSION_NOT_ACTIVE"));
    }

    @Test
    void create_alreadyActiveBreakout_returns409() throws Exception {
        mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("BREAKOUT_ALREADY_ACTIVE"));
    }

    // ─── T078: getActive ──────────────────────────────────────────────────────

    @Test
    void getActive_noBreakout_dataAbsent() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}/breakouts/active", sessionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void getActive_withActiveBreakout_returnsDto() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andExpect(status().isCreated())
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");

        mockMvc.perform(get("/api/v1/sessions/{id}/breakouts/active", sessionId)
                        .header("Authorization", "Bearer " + s1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breakoutSessionId").value(breakoutId))
                .andExpect(jsonPath("$.data.rooms.length()").value(2));
    }

    // ─── T078: end ────────────────────────────────────────────────────────────

    @Test
    void end_happyPath_setsEndedAtAndNullsActive() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/end", sessionId, breakoutId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.breakoutSessionId").value(breakoutId))
                .andExpect(jsonPath("$.data.endedAt").exists());

        // Active should now return null data
        mockMvc.perform(get("/api/v1/sessions/{id}/breakouts/active", sessionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void end_alreadyEnded_returns422() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/end", sessionId, breakoutId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/end", sessionId, breakoutId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BREAKOUT_ALREADY_ENDED"));
    }

    // ─── T078: broadcast ──────────────────────────────────────────────────────

    @Test
    void broadcast_happyPath_returnsRecipientCount() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/broadcast", sessionId, breakoutId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"5 minutes left!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sentAt").exists())
                .andExpect(jsonPath("$.data.recipientCount").value(3));
    }

    @Test
    void broadcast_toEndedBreakout_returns422() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/end", sessionId, breakoutId)
                        .header("Authorization", "Bearer " + teacherToken));

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/broadcast", sessionId, breakoutId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello!\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("BREAKOUT_ENDED"));
    }

    // ─── T078: joinRoom / leaveRoom ───────────────────────────────────────────

    @Test
    void joinRoom_happyPath_returnsRoomIdAndTimestamp() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");
        String roomId = at(createRes, "/data/rooms/0/id");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/rooms/{rid}/join",
                        sessionId, breakoutId, roomId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roomId").value(roomId))
                .andExpect(jsonPath("$.data.joinedAt").exists());
    }

    @Test
    void joinRoom_roomNotFound_returns404() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/rooms/{rid}/join",
                        sessionId, breakoutId, "00000000-0000-0000-0000-000000000000")
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void leaveRoom_happyPath_returns204() throws Exception {
        MvcResult createRes = mockMvc.perform(post("/api/v1/sessions/{id}/breakouts", sessionId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoRoomBody()))
                .andReturn();
        String breakoutId = at(createRes, "/data/breakoutSessionId");
        String roomId = at(createRes, "/data/rooms/1/id");

        mockMvc.perform(post("/api/v1/sessions/{sid}/breakouts/{bid}/rooms/{rid}/leave",
                        sessionId, breakoutId, roomId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isNoContent());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private UserInfo registerUser(String email, String name, Role role) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email);
        req.setPassword("password123");
        req.setName(name);
        req.setRole(role);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return new UserInfo(
                root.at("/data/user/id").asText(),
                root.at("/data/accessToken").asText());
    }

    private String at(MvcResult result, String path) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(path).asText();
    }

    private String twoRoomBody() throws Exception {
        return objectMapper.writeValueAsString(Map.of("rooms", List.of(
                Map.of("name", "Group A", "task", "Task A", "studentIds", List.of(s1Id, s2Id)),
                Map.of("name", "Group B", "task", "Task B", "studentIds", List.of(s3Id))
        )));
    }
}
