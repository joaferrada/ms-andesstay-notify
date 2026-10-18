package com.andesstay.ms_andesstay_notify.messaging;

import com.andesstay.ms_andesstay_notify.config.NotifyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Simulacion de fallos para la demo, controlada desde el payload del mensaje:
 * <ul>
 *   <li>{@code "simulateFailure": "TRANSIENT"}: fallo recuperable en todos los intentos -> reintentos -> DLQ</li>
 *   <li>{@code "simulateFailure": "TRANSIENT", "failTimes": 2}: falla 2 veces y el 3er intento tiene exito</li>
 *   <li>{@code "simulateFailure": "FATAL"}: excepcion inesperada -> DLQ sin reintentos</li>
 * </ul>
 * Se desactiva con andesstay.notify.simulation.enabled=false.
 */
@Component
public class FailureSimulator {

    static final String FIELD_MODE = "simulateFailure";
    static final String FIELD_TIMES = "failTimes";
    static final String MODE_TRANSIENT = "TRANSIENT";
    static final String MODE_FATAL = "FATAL";

    private final boolean enabled;

    @Autowired
    public FailureSimulator(NotifyProperties props) {
        this(props.simulation().enabled());
    }

    FailureSimulator(boolean enabled) {
        this.enabled = enabled;
    }

    public void apply(EventEnvelope envelope, int attempt) {
        if (!enabled) {
            return;
        }
        String mode = envelope.payloadText(FIELD_MODE);
        if (mode == null) {
            return;
        }
        if (MODE_FATAL.equalsIgnoreCase(mode)) {
            throw new IllegalStateException("Error inesperado simulado (FATAL)");
        }
        if (MODE_TRANSIENT.equalsIgnoreCase(mode)) {
            String times = envelope.payloadText(FIELD_TIMES);
            int failTimes = times == null ? Integer.MAX_VALUE : Integer.parseInt(times);
            if (attempt <= failTimes) {
                throw new RecoverableMessageException("Fallo transitorio simulado en intento " + attempt);
            }
        }
    }
}
