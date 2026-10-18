package com.andesstay.ms_andesstay_notify.messaging;

import org.springframework.amqp.core.Message;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.UUID;

/**
 * Convierte el body AMQP en {@link EventEnvelope} y valida los campos obligatorios del sobre.
 * Cualquier problema aqui es no recuperable: reintentar no arregla un JSON mal formado.
 */
@Component
public class EnvelopeParser {

    private final JsonMapper jsonMapper;

    public EnvelopeParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public EventEnvelope parse(Message message) {
        JsonNode root;
        try {
            root = jsonMapper.readTree(message.getBody());
        } catch (JacksonException ex) {
            throw new NonRecoverableMessageException("JSON invalido: " + ex.getOriginalMessage(), ex);
        }
        if (root == null || !root.isObject()) {
            throw new NonRecoverableMessageException("El body debe ser un objeto JSON");
        }

        String eventId = required(root, "eventId");
        try {
            UUID.fromString(eventId);
        } catch (IllegalArgumentException ex) {
            throw new NonRecoverableMessageException("eventId no es un UUID valido: " + eventId);
        }

        JsonNode payload = root.path("payload");
        if (!payload.isObject()) {
            throw new NonRecoverableMessageException("Campo obligatorio faltante o invalido: payload");
        }

        return new EventEnvelope(
                required(root, "type"),
                eventId,
                required(root, "timestamp"),
                optional(root, "traceId"),
                optional(root, "correlationId"),
                required(root, "source"),
                payload);
    }

    private static String required(JsonNode root, String field) {
        String value = optional(root, field);
        if (value == null) {
            throw new NonRecoverableMessageException("Campo obligatorio faltante: " + field);
        }
        return value;
    }

    private static String optional(JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value.isBlank() ? null : value;
    }
}
