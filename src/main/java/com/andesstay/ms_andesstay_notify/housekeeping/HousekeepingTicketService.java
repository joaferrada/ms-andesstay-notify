package com.andesstay.ms_andesstay_notify.housekeeping;

import com.andesstay.ms_andesstay_notify.messaging.EventEnvelope;
import com.andesstay.ms_andesstay_notify.messaging.FailureSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Creacion de tickets de housekeeping (simulada con logs).
 * Campos obligatorios: payload.reservationId y payload.alojamientoNombre.
 */
@Service
public class HousekeepingTicketService {

    private static final Logger log = LoggerFactory.getLogger(HousekeepingTicketService.class);
    private static final String TICKET_PREFIX = "HK-";

    private final FailureSimulator failureSimulator;

    public HousekeepingTicketService(FailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void createTicket(EventEnvelope envelope, int attempt) {
        String reservationId = envelope.requirePayload("reservationId");
        String alojamiento = envelope.requirePayload("alojamientoNombre");
        failureSimulator.apply(envelope, attempt);

        String ticketId = TICKET_PREFIX + reservationId + "-" + envelope.eventId().substring(0, 8);
        log.info("[HOUSEKEEPING] >>> Ticket {} creado tipo={} alojamiento={} reserva={} eventId={} traceId={}",
                ticketId, envelope.type(), alojamiento, reservationId, envelope.eventId(), envelope.traceId());
    }
}
