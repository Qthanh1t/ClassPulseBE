package com.classpulse.config;

import com.classpulse.common.security.JwtChannelInterceptor;
import com.classpulse.common.security.JwtHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtHandshakeHandler jwtHandshakeHandler;
    private final JwtChannelInterceptor jwtChannelInterceptor;

    // Shared with SecurityConfig (REST CORS). In production set
    // APP_CORS_ALLOWED_ORIGINS=https://<your-domain> → app.cors.allowed-origins.
    @Value("${app.cors.allowed-origins:http://localhost:5173,http://localhost:*,https://localhost:*,http://192.168.*:*,https://192.168.*:*,http://10.*:*,https://10.*:*}")
    private List<String> allowedOriginPatterns;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory simple broker. For multi-instance production, replace with:
        // registry.enableStompBrokerRelay("/topic", "/queue")
        //         .setRelayHost(host).setRelayPort(61613)...;
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setHandshakeHandler(jwtHandshakeHandler)
                .setAllowedOriginPatterns(allowedOriginPatterns.toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
