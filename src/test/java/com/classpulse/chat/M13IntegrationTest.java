package com.classpulse.chat;

import com.classpulse.auth.RefreshTokenRepository;
import com.classpulse.auth.RegisterRequest;
import com.classpulse.breakout.BreakoutSessionRepository;
import com.classpulse.classroom.ClassroomRepository;
import com.classpulse.classroom.MembershipRepository;
import com.classpulse.session.RaisedHandRepository;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class M13IntegrationTest {

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

    @LocalServerPort int port;
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired ChatRepository chatRepository;
    @Autowired RaisedHandRepository raisedHandRepository;
    @Autowired BreakoutSessionRepository breakoutSessionRepository;
    @Autowired SessionRepository sessionRepository;
    @Autowired MembershipRepository membershipRepository;
    @Autowired ClassroomRepository classroomRepository;
    @Autowired RefreshTokenRepository refreshTokenRepository;
    @Autowired UserRepository userRepository;

    String teacherToken, teacherId;
    String s1Token, s1Id;
    String sessionId;
    String s1WsTicket; // session-scoped ticket from join response

    record UserInfo(String id, String token) {}

    @BeforeEach
    void setUp() throws Exception {
        // Cleanup in FK-safe order
        chatRepository.deleteAllInBatch();
        raisedHandRepository.deleteAllInBatch();
        breakoutSessionRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        membershipRepository.deleteAllInBatch();
        classroomRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        var teacher = registerUser("teacher@test.com", "Teacher One", Role.TEACHER);
        var s1 = registerUser("s1@test.com", "Student One", Role.STUDENT);
        teacherToken = teacher.token(); teacherId = teacher.id();
        s1Token = s1.token(); s1Id = s1.id();

        MvcResult classRes = mockMvc.perform(post("/api/v1/classrooms")
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test Classroom\"}"))
                .andExpect(status().isCreated()).andReturn();
        String classroomId = at(classRes, "/data/id");
        String joinCode = at(classRes, "/data/joinCode");

        mockMvc.perform(post("/api/v1/classrooms/join")
                        .header("Authorization", "Bearer " + s1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"joinCode\":\"" + joinCode + "\"}"))
                .andExpect(status().isOk());

        MvcResult sessionRes = mockMvc.perform(post("/api/v1/classrooms/{id}/sessions", classroomId)
                        .header("Authorization", "Bearer " + teacherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated()).andReturn();
        sessionId = at(sessionRes, "/data/id");

        // Student joins session → presence record + session-scoped wsTicket
        MvcResult joinRes = mockMvc.perform(post("/api/v1/sessions/{id}/join", sessionId)
                        .header("Authorization", "Bearer " + s1Token))
                .andExpect(status().isOk()).andReturn();
        s1WsTicket = at(joinRes, "/data/wsTicket");
    }

    // ─── REST: chat history ───────────────────────────────────────────────────

    @Test
    void chatHistory_noMessages_returnsEmptyListWithNoMore() throws Exception {
        mockMvc.perform(get("/api/v1/sessions/{id}/chat", sessionId)
                        .header("Authorization", "Bearer " + s1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.hasMore").value(false))
                .andExpect(jsonPath("$.meta.oldestId").doesNotExist());
    }

    @Test
    void chatHistory_nonParticipant_returns403() throws Exception {
        var outsider = registerUser("outsider@test.com", "Outsider", Role.STUDENT);
        mockMvc.perform(get("/api/v1/sessions/{id}/chat", sessionId)
                        .header("Authorization", "Bearer " + outsider.token()))
                .andExpect(status().isForbidden());
    }

    @Test
    void chatHistory_teacherReads_returnsChronologicalOrder() throws Exception {
        seedMessage("First message");
        Thread.sleep(10);
        seedMessage("Second message");

        MvcResult result = mockMvc.perform(get("/api/v1/sessions/{id}/chat", sessionId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(false))
                .andReturn();

        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).at("/data");
        // Service reverses DESC results → chronological (oldest first)
        assertThat(data.get(0).at("/content").asText()).isEqualTo("First message");
        assertThat(data.get(1).at("/content").asText()).isEqualTo("Second message");
        assertThat(data.get(0).at("/sender/role").asText()).isEqualTo("teacher");
    }

    @Test
    void chatHistory_cursorPagination_hasMoreAndOldestId() throws Exception {
        // Seed 3 messages with distinct timestamps
        seedMessage("Msg 1"); Thread.sleep(10);
        seedMessage("Msg 2"); Thread.sleep(10);
        seedMessage("Msg 3");

        // First page: limit=2 → returns 2 newest (Msg2, Msg3), hasMore=true
        MvcResult page1 = mockMvc.perform(
                        get("/api/v1/sessions/{id}/chat?limit=2", sessionId)
                                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.meta.hasMore").value(true))
                .andExpect(jsonPath("$.meta.oldestId").isNotEmpty())
                .andReturn();

        String oldestId = objectMapper.readTree(page1.getResponse().getContentAsString())
                .at("/meta/oldestId").asText();

        // Second page: before cursor → returns remaining older message (Msg1)
        mockMvc.perform(get("/api/v1/sessions/{id}/chat?limit=2&before={cursor}", sessionId, oldestId)
                        .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].content").value("Msg 1"))
                .andExpect(jsonPath("$.meta.hasMore").value(false));
    }

    @Test
    void chatHistory_limitParam_boundedCorrectly() throws Exception {
        // limit must be between 1 and 100 inclusive
        mockMvc.perform(get("/api/v1/sessions/{id}/chat?limit=0", sessionId)
                        .header("Authorization", "Bearer " + s1Token))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/sessions/{id}/chat?limit=101", sessionId)
                        .header("Authorization", "Bearer " + s1Token))
                .andExpect(status().isBadRequest());
    }

    // ─── WebSocket: chat ──────────────────────────────────────────────────────

    @Test
    void wsChat_teacherSends_broadcastReceivedOnSessionTopic() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        // Teacher obtains a simple WS ticket (no session context needed for teacher)
        String teacherTicket = getSimpleWsTicket(teacherToken);
        StompSession stomp = connectWs(teacherTicket);
        stomp.subscribe("/topic/session/" + sessionId, frameHandler(received));
        Thread.sleep(300); // let subscription propagate

        stomp.send("/app/session/" + sessionId + "/chat",
                Map.of("content", "Hello class!"));

        Map<String, Object> event = received.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.get("type")).isEqualTo("chat_message");

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload.get("content")).isEqualTo("Hello class!");
        assertThat(payload.get("senderRole")).isEqualTo("teacher");
        assertThat(payload.get("sentAt")).isNotNull();

        stomp.disconnect();
    }

    @Test
    void wsChat_persistsMessageInDB() throws Exception {
        String teacherTicket = getSimpleWsTicket(teacherToken);
        StompSession stomp = connectWs(teacherTicket);
        Thread.sleep(200);

        stomp.send("/app/session/" + sessionId + "/chat",
                Map.of("content", "Persisted message"));
        Thread.sleep(500); // wait for async DB save

        stomp.disconnect();

        assertThat(chatRepository.count()).isEqualTo(1);
        ChatMessage saved = chatRepository.findAll().get(0);
        assertThat(saved.getContent()).isEqualTo("Persisted message");
        assertThat(saved.getSentAt()).isNotNull();
    }

    // ─── WebSocket: raise hand ────────────────────────────────────────────────

    @Test
    void wsRaiseHand_studentRaises_broadcastReceivedByAll() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();

        // Student connects with session-scoped ticket (includes sessionId for presence)
        StompSession stomp = connectWs(s1WsTicket);
        stomp.subscribe("/topic/session/" + sessionId, frameHandler(received));
        Thread.sleep(300);

        stomp.send("/app/session/" + sessionId + "/raise-hand", Map.of("raised", true));

        // Drain any presence events emitted on connect
        Map<String, Object> event = drainUntil(received, "raise_hand_changed");
        assertThat(event).isNotNull();

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload.get("raised")).isEqualTo(true);
        assertThat(payload.get("studentId")).isEqualTo(s1Id);

        stomp.disconnect();
    }

    @Test
    void wsRaiseHand_lowerHand_broadcastsRaisedFalse() throws Exception {
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        StompSession stomp = connectWs(s1WsTicket);
        stomp.subscribe("/topic/session/" + sessionId, frameHandler(received));
        Thread.sleep(300);

        stomp.send("/app/session/" + sessionId + "/raise-hand", Map.of("raised", false));

        Map<String, Object> event = drainUntil(received, "raise_hand_changed");
        assertThat(event).isNotNull();
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) event.get("payload");
        assertThat(payload.get("raised")).isEqualTo(false);

        stomp.disconnect();
    }

    @Test
    void wsRaiseHand_teacherIgnored_noBroadcast() throws Exception {
        // Teachers cannot raise hand — RaiseHandWsController silently ignores non-STUDENT
        BlockingQueue<Map<String, Object>> received = new LinkedBlockingQueue<>();
        String teacherTicket = getSimpleWsTicket(teacherToken);
        StompSession stomp = connectWs(teacherTicket);
        stomp.subscribe("/topic/session/" + sessionId, frameHandler(received));
        Thread.sleep(300);

        stomp.send("/app/session/" + sessionId + "/raise-hand", Map.of("raised", true));
        Thread.sleep(500);

        // No raise_hand_changed event should be in queue
        Map<String, Object> event = drainUntil(received, "raise_hand_changed");
        assertThat(event).isNull();

        stomp.disconnect();
    }

    // ─── WebSocket: heartbeat ─────────────────────────────────────────────────

    @Test
    void wsHeartbeat_keepsConnectionAlive_noError() throws Exception {
        String teacherTicket = getSimpleWsTicket(teacherToken);
        StompSession stomp = connectWs(teacherTicket);
        Thread.sleep(100);

        stomp.send("/app/session/" + sessionId + "/heartbeat", Map.of());
        Thread.sleep(200);

        assertThat(stomp.isConnected()).isTrue();
        stomp.disconnect();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private StompSession connectWs(String ticket) throws Exception {
        List<Transport> transports = List.of(
                new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient client = new WebSocketStompClient(new SockJsClient(transports));
        client.setMessageConverter(new MappingJackson2MessageConverter());
        return client.connectAsync(
                        "http://localhost:" + port + "/ws?ticket=" + ticket,
                        new StompSessionHandlerAdapter() {})
                .get(5, TimeUnit.SECONDS);
    }

    private String getSimpleWsTicket(String bearerToken) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/auth/ws-ticket")
                        .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk()).andReturn();
        return at(res, "/data/ticket");
    }

    @SuppressWarnings("unchecked")
    private StompFrameHandler frameHandler(BlockingQueue<Map<String, Object>> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) { return Map.class; }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((Map<String, Object>) payload);
            }
        };
    }

    /** Poll queue until an event with the expected type is found (skip others). */
    private Map<String, Object> drainUntil(BlockingQueue<Map<String, Object>> queue,
                                            String expectedType) throws InterruptedException {
        Map<String, Object> event;
        long deadline = System.currentTimeMillis() + 5_000;
        while ((event = queue.poll(500, TimeUnit.MILLISECONDS)) != null) {
            if (expectedType.equals(event.get("type"))) return event;
            if (System.currentTimeMillis() > deadline) break;
        }
        return null;
    }

    /** Seed a chat message directly via repository (bypasses WS layer). */
    private void seedMessage(String content) {
        chatRepository.save(ChatMessage.builder()
                .session(sessionRepository.getReferenceById(UUID.fromString(sessionId)))
                .sender(userRepository.getReferenceById(UUID.fromString(teacherId)))
                .content(content)
                .build());
    }

    private UserInfo registerUser(String email, String name, Role role) throws Exception {
        RegisterRequest req = new RegisterRequest();
        req.setEmail(email); req.setPassword("password123");
        req.setName(name); req.setRole(role);
        MvcResult res = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk()).andReturn();
        JsonNode root = objectMapper.readTree(res.getResponse().getContentAsString());
        return new UserInfo(root.at("/data/user/id").asText(), root.at("/data/accessToken").asText());
    }

    private String at(MvcResult result, String path) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).at(path).asText();
    }
}
