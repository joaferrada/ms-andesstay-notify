package com.andesstay.ms_andesstay_notify.messaging;

/**
 * Que hacer con un mensaje despues de intentar procesarlo.
 */
public enum AckDecision {
    /** Procesado OK: basicAck. */
    ACK,
    /** eventId ya procesado: basicAck sin reprocesar (idempotencia). */
    ACK_DUPLICATE,
    /** Fallo transitorio con intentos disponibles: republicar copia con x-retry-count+1 y basicAck del original. */
    RETRY,
    /** No recuperable o intentos agotados: basicNack(requeue=false) y RabbitMQ lo envia a la DLQ via DLX. */
    REJECT
}
