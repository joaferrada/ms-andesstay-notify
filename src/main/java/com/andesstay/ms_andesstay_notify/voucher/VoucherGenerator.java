package com.andesstay.ms_andesstay_notify.voucher;

import com.andesstay.ms_andesstay_notify.messaging.EventEnvelope;
import com.andesstay.ms_andesstay_notify.messaging.FailureSimulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Generacion de vouchers y boletas en PDF (simulada con logs).
 * Campos obligatorios: payload.reservationId y payload.clienteUsername.
 */
@Service
public class VoucherGenerator {

    private static final Logger log = LoggerFactory.getLogger(VoucherGenerator.class);
    private static final String TYPE_BOLETA = "BOLETA_GENERAR";

    private final FailureSimulator failureSimulator;

    public VoucherGenerator(FailureSimulator failureSimulator) {
        this.failureSimulator = failureSimulator;
    }

    public void generate(EventEnvelope envelope, int attempt) {
        String reservationId = envelope.requirePayload("reservationId");
        String cliente = envelope.requirePayload("clienteUsername");
        failureSimulator.apply(envelope, attempt);

        String documento = TYPE_BOLETA.equals(envelope.type()) ? "boleta" : "voucher";
        log.info("[VOUCHER] >>> PDF generado {}-{}.pdf para={} monto={} eventId={} traceId={}",
                documento, reservationId, cliente, envelope.payloadText("precioTotal"),
                envelope.eventId(), envelope.traceId());
    }
}
