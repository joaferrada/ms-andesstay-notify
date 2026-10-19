package com.andesstay.ms_andesstay_notify.email;

import com.andesstay.ms_andesstay_notify.messaging.EventEnvelope;
import com.andesstay.ms_andesstay_notify.messaging.FailureSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Envio de correos (simulado con logs).
 * Campos obligatorios: payload.reservationId y el destinatario: payload.huespedEmail
 * (o payload.clienteUsername en mensajes antiguos, por compatibilidad).
 */
@Service
public class EmailSender {

    private static final Logger log = LoggerFactory.getLogger(EmailSender.class);

    private static final Map<String, String> ASUNTOS = Map.of(
            "EMAIL_RESERVA_CREADA", "Recibimos tu reserva",
            "EMAIL_RESERVA_CONFIRMADA", "Tu reserva esta confirmada",
            "EMAIL_RECORDATORIO_CHECKIN", "Recordatorio: tu check-in se acerca",
            "EMAIL_CHECKOUT", "Gracias por tu estadia",
            "EMAIL_RESERVA_CANCELADA", "Tu reserva fue cancelada");

    private final FailureSimulator failureSimulator;

    public EmailSender(FailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void send(EventEnvelope envelope, int attempt) {
        String reservationId = envelope.requirePayload("reservationId");
        String destinatario = envelope.requireFirstPayload("huespedEmail", "clienteUsername");
        failureSimulator.apply(envelope, attempt);

        String asunto = ASUNTOS.getOrDefault(envelope.type(), "Notificacion AndesStay");
        log.info("[EMAIL] >>> Enviado a={} asunto=\"{}\" reserva={} alojamiento={} eventId={} traceId={}",
                destinatario, asunto, reservationId, envelope.payloadText("alojamientoNombre"),
                envelope.eventId(), envelope.traceId());
    }
}
