package com.andesstay.ms_andesstay_notify.messaging;

/**
 * Error que no se arregla reintentando (JSON invalido, campos faltantes, datos imposibles).
 * Resultado: basicNack(requeue=false) y el mensaje termina en la DLQ.
 */
public class NonRecoverableMessageException extends RuntimeException {

    public NonRecoverableMessageException(String message) {
        super(message);
    }

    public NonRecoverableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}
