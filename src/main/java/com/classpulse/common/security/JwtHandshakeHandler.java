package com.classpulse.common.security;

import com.classpulse.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeHandler extends DefaultHandshakeHandler {

    private final WsTicketService wsTicketService;
    private final UserRepository userRepository;

    @Override
    protected Principal determineUser(ServerHttpRequest request,
                                      WebSocketHandler wsHandler,
                                      Map<String, Object> attributes) {
        String ticket = extractTicket(request);
        if (ticket == null) {
            log.warn("WS handshake rejected: missing ticket query param");
            return null;
        }

        UUID userId = wsTicketService.validateAndConsume(ticket).orElse(null);
        if (userId == null) {
            log.warn("WS handshake rejected: invalid or expired ticket");
            return null;
        }

        return userRepository.findById(userId)
                .map(user -> {
                    attributes.put("userId", user.getId().toString());
                    attributes.put("userRole", user.getRole().name());
                    log.info("WS handshake accepted: user={} role={}", user.getId(), user.getRole());
                    return (Principal) new StompPrincipal(user.getId(), user.getRole(), user.getName());
                })
                .orElseGet(() -> {
                    log.warn("WS handshake rejected: user not found for id={}", userId);
                    return null;
                });
    }

    private String extractTicket(ServerHttpRequest request) {
        String query = request.getURI().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            String[] kv = param.split("=", 2);
            if (kv.length == 2 && "ticket".equals(kv[0])) {
                return kv[1];
            }
        }
        return null;
    }
}
