package com.andesstay.ms_andesstay_notify.messaging;

/**
 * Logica de un dominio (email, housekeeping, voucher). Lanza
 * {@link NonRecoverableMessageException} o {@link RecoverableMessageException} segun el tipo de error.
 */
@FunctionalInterface
public interface CommandHandler {

    /**
     * @param attempt numero de intento (1 = primera entrega)
     */
    void handle(EventEnvelope envelope, int attempt);
}
