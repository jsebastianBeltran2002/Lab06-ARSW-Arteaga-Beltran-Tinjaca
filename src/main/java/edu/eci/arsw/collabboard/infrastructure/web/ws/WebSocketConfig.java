package edu.eci.arsw.collabboard.infrastructure.web.ws;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP/WebSocket transport configuration, kept apart from the REST controllers.
 *
 * <ul>
 *     <li>{@code /ws} — WebSocket handshake endpoint (same-origin only).</li>
 *     <li>{@code /app} — prefix for messages routed to {@code @MessageMapping} handlers.</li>
 *     <li>{@code /topic} — simple in-memory broker for per-board broadcasts.</li>
 *     <li>{@code /queue} + {@code /user} — private channel used only to tell the
 *     sender that its event was rejected.</li>
 * </ul>
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws");
    }
}
