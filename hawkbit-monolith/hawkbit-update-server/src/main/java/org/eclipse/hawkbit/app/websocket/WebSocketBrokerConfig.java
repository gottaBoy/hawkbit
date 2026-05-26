/**
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.hawkbit.app.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Configures a STOMP-over-SockJS endpoint so the management UI can receive
 * real-time event notifications via WebSocket instead of polling.
 *
 * <p>Endpoint: {@code /ws/stomp} (SockJS-enabled, auto-provides {@code /ws/stomp/info}).
 * <p>Topic prefix: {@code /topic} — all events are published under {@code /topic/events/*}.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketBrokerConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(final MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
    }

    @Override
    public void registerStompEndpoints(final StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/stomp")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
