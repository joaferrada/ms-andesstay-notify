package com.andesstay.ms_andesstay_notify.messaging;

import com.andesstay.ms_andesstay_notify.config.NotifyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Reglas de ACK/NACK (logica pura, sin RabbitMQ, testeable de forma aislada).
 *
 * <table>
 *   <tr><th>Situacion</th><th>Decision</th></tr>
 *   <tr><td>Exito</td><td>ACK</td></tr>
 *   <tr><td>eventId ya procesado</td><td>ACK_DUPLICATE</td></tr>
 *   <tr><td>{@link NonRecoverableMessageException}</td><td>REJECT (DLQ)</td></tr>
 *   <tr><td>{@link RecoverableMessageException} con intento &lt; max-attempts</td><td>RETRY</td></tr>
 *   <tr><td>{@link RecoverableMessageException} en el ultimo intento</td><td>REJECT (DLQ)</td></tr>
 *   <tr><td>Cualquier otra excepcion (inesperada)</td><td>REJECT (DLQ)</td></tr>
 * </table>
 * Nunca se usa requeue=true, asi que no hay loops infinitos.
 */
@Component
public class AckDecisionPolicy {

    private final int maxAttempts;

    @Autowired
    public AckDecisionPolicy(NotifyProperties props) {
        this(props.retry().maxAttempts());
    }

    AckDecisionPolicy(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public AckDecision onSuccess() {
        return AckDecision.ACK;
    }

    public AckDecision onDuplicate() {
        return AckDecision.ACK_DUPLICATE;
    }

    /**
     * @param retryCount valor del header x-retry-count (0 en la primera entrega)
     */
    public AckDecision onFailure(Throwable error, int retryCount) {
        if (error instanceof RecoverableMessageException) {
            int attempt = retryCount + 1;
            return attempt < maxAttempts ? AckDecision.RETRY : AckDecision.REJECT;
        }
        // NonRecoverableMessageException y cualquier excepcion inesperada
        return AckDecision.REJECT;
    }

    public int maxAttempts() {
        return maxAttempts;
    }
}
