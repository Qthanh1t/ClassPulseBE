package com.classpulse.config;

import com.classpulse.common.security.JwtChannelInterceptor;
import com.classpulse.common.security.JwtHandshakeHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
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
                .setAllowedOriginPatterns(
                        "http://localhost:5173",
                        "http://localhost:*",       // dev + integration tests
                        "http://192.168.*:*",       // LAN testing (phone on same network)
                        "http://10.*:*",            // LAN testing (10.x.x.x range)
                        "https://classpulse.app")
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
