package com.andesstay.ms_andesstay_notify.messaging;

import tools.jackson.databind.JsonNode;

/**
 * Sobre comun de los mensajes de AndesStay (lado consumidor). El payload se mantiene como
 * arbol JSON porque cada dominio valida sus propios campos obligatorios.
 */
public record EventEnvelope(
        String type,
        String eventId,
        String timestamp,
        String traceId,
        String correlationId,
        String source,
        JsonNode payload) {

    /** Texto de un campo del payload, o {@code null} si no viene o esta vacio. */
    public String payloadText(String field) {
        JsonNode node = payload.path(field);
        if (node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.asString();
        return value.isBlank() ? null : value;
    }

    /** Exige un campo del payload; si falta el mensaje es no recuperable (va a la DLQ). */
    public String requirePayload(String field) {
        String value = payloadText(field);
        if (value == null) {
            throw new NonRecoverableMessageException("Campo obligatorio faltante: payload." + field);
        }
        return value;
    }

    /**
     * Exige el primero de varios campos alternativos (en orden de preferencia).
     * Si no viene ninguno, el mensaje es no recuperable (va a la DLQ).
     */
    public String requireFirstPayload(String... fields) {
        for (String field : fields) {
            String value = payloadText(field);
            if (value != null) {
                return value;
            }
        }
        throw new NonRecoverableMessageException("Campo obligatorio faltante: payload." + String.join(" o payload.", fields));
    }
}
