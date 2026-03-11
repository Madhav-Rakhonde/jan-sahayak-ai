package com.JanSahayak.AI.config;

import com.JanSahayak.AI.model.User;
import com.JanSahayak.AI.repository.UserRepo;
import com.JanSahayak.AI.security.JwtUtil;
import com.JanSahayak.AI.service.ChatSessionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * WebSocket configuration + disconnect handler.
 *
 * ── Principal name ────────────────────────────────────────────────────────────
 * The STOMP principal is stored as:
 *   new UsernamePasswordAuthenticationToken(user, null, authorities)
 * so Authentication.getName() == user.getUsername() == EMAIL.
 *
 * convertAndSendToUser(email, dest, payload) therefore routes correctly, and
 * @AuthenticationPrincipal resolves to the actual User entity.
 *
 * ── Disconnect handling ───────────────────────────────────────────────────────
 * This class also implements ApplicationListener<SessionDisconnectEvent>.
 * Spring calls onApplicationEvent() automatically whenever any WebSocket
 * session closes (refresh, tab close, network drop).
 * event.getUser().getName() == email → passed to handleUserDisconnectByEmail().
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer,
        ApplicationListener<SessionDisconnectEvent> {

    private final JwtUtil            jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepo           userRepo;
    private final ChatSessionService chatSessionService;

    public WebSocketConfig(
            JwtUtil jwtUtil,
            @Qualifier("customUserDetailsService") UserDetailsService userDetailsService,
            UserRepo userRepo,
            ChatSessionService chatSessionService
    ) {
        this.jwtUtil             = jwtUtil;
        this.userDetailsService  = userDetailsService;
        this.userRepo            = userRepo;
        this.chatSessionService  = chatSessionService;
    }

    // ── Broker & endpoints ────────────────────────────────────────────────────

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    // ── Inbound channel interceptor (JWT auth on CONNECT) ────────────────────

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                        message, StompHeaderAccessor.class);

                if (accessor == null || !StompCommand.CONNECT.equals(accessor.getCommand())) {
                    return message;
                }

                String authHeader = accessor.getFirstNativeHeader("Authorization");
                if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                    log.warn("WebSocket CONNECT without Authorization header");
                    throw new IllegalArgumentException("Missing Authorization header");
                }

                String token = authHeader.substring(7);

                try {
                    if (jwtUtil.isTokenExpired(token)) {
                        throw new IllegalArgumentException("Token expired — please log in again");
                    }
                    if (!jwtUtil.validateToken(token)) {
                        throw new IllegalArgumentException("Invalid token");
                    }

                    String usernameFromJwt = jwtUtil.getUsernameFromToken(token);
                    if (usernameFromJwt == null) {
                        throw new IllegalArgumentException("Token has no subject");
                    }

                    // JWT subject = USERNAME; look up by username (not email)
                    User user = userRepo.findByUsername(usernameFromJwt)
                            .orElseThrow(() -> new IllegalArgumentException(
                                    "User not found: " + usernameFromJwt));

                    // Store User as principal so that:
                    //   @AuthenticationPrincipal User user  → resolves correctly ✓
                    //   Authentication.getName()            → user.getUsername() == email ✓
                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(
                                    user,
                                    null,
                                    user.getAuthorities()
                            );

                    accessor.setUser(auth);

                    log.info("WebSocket authenticated: username={} principalName(email)={}",
                            usernameFromJwt, user.getEmail());

                } catch (io.jsonwebtoken.ExpiredJwtException e) {
                    log.error("WebSocket CONNECT rejected: token expired");
                    throw new IllegalArgumentException("Token expired — please log in again");
                } catch (IllegalArgumentException e) {
                    log.error("WebSocket CONNECT rejected: {}", e.getMessage());
                    throw e;
                } catch (Exception e) {
                    log.error("WebSocket authentication failed unexpectedly", e);
                    throw new IllegalArgumentException("Authentication failed: " + e.getMessage());
                }

                return message;
            }
        });
    }

    // ── WebSocket disconnect handler ──────────────────────────────────────────

    /**
     * Called automatically by Spring whenever a WebSocket session closes.
     *
     * event.getUser().getName() == email (because the principal was stored as
     * a UsernamePasswordAuthenticationToken(user, ...) and
     * user.getUsername() returns EMAIL in this project).
     *
     * Delegates to ChatSessionService.handleUserDisconnectByEmail() which
     * starts the reconnect grace period so a page-refresh does NOT hard-end
     * the chat session immediately.
     */
    @Override
    public void onApplicationEvent(SessionDisconnectEvent event) {
        Principal principal = event.getUser();
        if (principal == null) {
            log.debug("WebSocket disconnect with no principal — ignoring");
            return;
        }

        String email = principal.getName(); // == user.getUsername() == email
        log.info("WebSocket disconnected: principal(email)={}", email);

        try {
            chatSessionService.handleUserDisconnectByEmail(email);
        } catch (Exception e) {
            log.error("Error handling WebSocket disconnect for {}", email, e);
        }
    }
}