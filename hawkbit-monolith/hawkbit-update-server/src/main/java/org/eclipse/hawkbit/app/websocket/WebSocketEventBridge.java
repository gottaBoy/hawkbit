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

import java.util.LinkedHashMap;
import java.util.Map;

import org.eclipse.hawkbit.repository.event.remote.RolloutStoppedEvent;
import org.eclipse.hawkbit.repository.event.remote.TargetPollEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.AbstractActionEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.ActionUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.DistributionSetUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RemoteEntityEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutGroupUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.RolloutUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.SoftwareModuleUpdatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetCreatedEvent;
import org.eclipse.hawkbit.repository.event.remote.entity.TargetUpdatedEvent;
import org.eclipse.hawkbit.repository.model.Action;
import org.eclipse.hawkbit.repository.model.Rollout;
import org.eclipse.hawkbit.repository.model.Target;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Bridges hawkBit's internal Spring {@link org.springframework.context.ApplicationEvent}s
 * to STOMP topics so the management UI receives real-time push notifications.
 *
 * <p>Topic mapping:
 * <ul>
 *   <li>{@code /topic/events/actions}    — Action CRUD</li>
 *   <li>{@code /topic/events/targets}    — Target CRUD + poll</li>
 *   <li>{@code /topic/events/rollouts}   — Rollout CRUD + stopped + group events</li>
 *   <li>{@code /topic/events/repository} — DistributionSet / SoftwareModule CRUD</li>
 *   <li>{@code /topic/events/system}     — reserved for future system-config events</li>
 * </ul>
 *
 * <p>Message format (JSON):
 * <pre>{
 *   "eventType": "CREATED|UPDATE|DELETED|STOPPED|POLL",
 *   "entityType": "ACTION|TARGET|ROLLOUT|...",
 *   "entityClass": "DistributionSet",   // optional, for repository topic
 *   "payload": { ... },
 *   "timestamp": 1711234567890
 * }</pre>
 */
@Component
public class WebSocketEventBridge {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketEventBridge.class);

    private static final String TOPIC_ACTIONS    = "/topic/events/actions";
    private static final String TOPIC_TARGETS    = "/topic/events/targets";
    private static final String TOPIC_ROLLOUTS   = "/topic/events/rollouts";
    private static final String TOPIC_REPOSITORY = "/topic/events/repository";

    private final SimpMessagingTemplate messaging;

    public WebSocketEventBridge(final SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    // ── Actions ─────────────────────────────────────────────────────────

    @EventListener
    public void onActionCreated(final ActionCreatedEvent event) {
        send(TOPIC_ACTIONS, "CREATED", "ACTION", null, actionPayload(event));
    }

    @EventListener
    public void onActionUpdated(final ActionUpdatedEvent event) {
        send(TOPIC_ACTIONS, "UPDATE", "ACTION", null, actionPayload(event));
    }

    // ── Targets ─────────────────────────────────────────────────────────

    @EventListener
    public void onTargetCreated(final TargetCreatedEvent event) {
        send(TOPIC_TARGETS, "CREATED", "TARGET", null, targetPayload(event));
    }

    @EventListener
    public void onTargetUpdated(final TargetUpdatedEvent event) {
        send(TOPIC_TARGETS, "UPDATE", "TARGET", null, targetPayload(event));
    }

    @EventListener
    public void onTargetPoll(final TargetPollEvent event) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("controllerId", event.getControllerId());
        send(TOPIC_TARGETS, "POLL", "TARGET", null, payload);
    }

    // ── Rollouts ────────────────────────────────────────────────────────

    @EventListener
    public void onRolloutCreated(final RolloutCreatedEvent event) {
        send(TOPIC_ROLLOUTS, "CREATED", "ROLLOUT", null, rolloutPayload(event));
    }

    @EventListener
    public void onRolloutUpdated(final RolloutUpdatedEvent event) {
        send(TOPIC_ROLLOUTS, "UPDATE", "ROLLOUT", null, rolloutPayload(event));
    }

    @EventListener
    public void onRolloutStopped(final RolloutStoppedEvent event) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getRolloutId());
        send(TOPIC_ROLLOUTS, "STOPPED", "ROLLOUT", null, payload);
    }

    @EventListener
    public void onRolloutGroupCreated(final RolloutGroupCreatedEvent event) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getEntityId());
        send(TOPIC_ROLLOUTS, "CREATED", "ROLLOUT_GROUP", null, payload);
    }

    @EventListener
    public void onRolloutGroupUpdated(final RolloutGroupUpdatedEvent event) {
        final Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", event.getEntityId());
        send(TOPIC_ROLLOUTS, "UPDATE", "ROLLOUT_GROUP", null, payload);
    }

    // ── Repository (DistributionSet / SoftwareModule) ───────────────────

    @EventListener
    public void onDistributionSetCreated(final DistributionSetCreatedEvent event) {
        send(TOPIC_REPOSITORY, "CREATED", "DistributionSet", "DistributionSet", idPayload(event));
    }

    @EventListener
    public void onDistributionSetUpdated(final DistributionSetUpdatedEvent event) {
        send(TOPIC_REPOSITORY, "UPDATE", "DistributionSet", "DistributionSet", idPayload(event));
    }

    @EventListener
    public void onSoftwareModuleCreated(final SoftwareModuleCreatedEvent event) {
        send(TOPIC_REPOSITORY, "CREATED", "SoftwareModule", "SoftwareModule", idPayload(event));
    }

    @EventListener
    public void onSoftwareModuleUpdated(final SoftwareModuleUpdatedEvent event) {
        send(TOPIC_REPOSITORY, "UPDATE", "SoftwareModule", "SoftwareModule", idPayload(event));
    }

    // ── Payload helpers ─────────────────────────────────────────────────

    private static Map<String, Object> actionPayload(final AbstractActionEvent event) {
        final Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", event.getEntityId());
        if (event.getTargetId() != null) {
            p.put("targetId", String.valueOf(event.getTargetId()));
        }
        try {
            event.getEntity().ifPresent(action -> {
                if (action.getStatus() != null) {
                    p.put("status", action.getStatus().name());
                }
            });
        } catch (final Exception e) {
            LOG.trace("Could not enrich action payload from entity", e);
        }
        return p;
    }

    private static Map<String, Object> targetPayload(final RemoteEntityEvent<Target> event) {
        final Map<String, Object> p = new LinkedHashMap<>();
        try {
            event.getEntity().ifPresent(target -> {
                p.put("controllerId", target.getControllerId());
                if (target.getUpdateStatus() != null) {
                    p.put("status", target.getUpdateStatus().name());
                }
            });
        } catch (final Exception e) {
            LOG.trace("Could not enrich target payload from entity", e);
        }
        if (!p.containsKey("controllerId")) {
            p.put("controllerId", String.valueOf(event.getEntityId()));
        }
        return p;
    }

    private static Map<String, Object> rolloutPayload(final RemoteEntityEvent<Rollout> event) {
        final Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", event.getEntityId());
        try {
            event.getEntity().ifPresent(rollout -> {
                if (rollout.getStatus() != null) {
                    p.put("status", rollout.getStatus().name());
                }
            });
        } catch (final Exception e) {
            LOG.trace("Could not enrich rollout payload from entity", e);
        }
        return p;
    }

    private static Map<String, Object> idPayload(final RemoteEntityEvent<?> event) {
        final Map<String, Object> p = new LinkedHashMap<>();
        p.put("id", event.getEntityId());
        return p;
    }

    private void send(final String topic, final String eventType, final String entityType,
                      final String entityClass, final Map<String, Object> payload) {
        final Map<String, Object> message = new LinkedHashMap<>();
        message.put("eventType", eventType);
        message.put("entityType", entityType);
        if (entityClass != null) {
            message.put("entityClass", entityClass);
        }
        message.put("payload", payload);
        message.put("timestamp", System.currentTimeMillis());

        try {
            messaging.convertAndSend(topic, message);
            if (LOG.isDebugEnabled()) {
                LOG.debug("WS → {} {} {}", topic, eventType, entityType);
            }
        } catch (final Exception e) {
            LOG.warn("Failed to send WebSocket message to {}: {}", topic, e.getMessage());
        }
    }
}
