package com.andesstay.ms_andesstay_notify.messaging;

/**
 * Fallo transitorio (servidor SMTP caido, timeout, etc.). Se reintenta de forma controlada
 * hasta max-attempts; despues el mensaje va a la DLQ.
 */
public class RecoverableMessageException extends RuntimeException {

    public RecoverableMessageException(String message) {
        super(message);
    }
}
