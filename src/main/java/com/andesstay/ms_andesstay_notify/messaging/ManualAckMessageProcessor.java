package com.andesstay.ms_andesstay_notify.messaging;

import com.andesstay.ms_andesstay_notify.config.NotifyProperties;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

/**
 * Flujo comun de todos los listeners con ACK manual:
 *
 * <pre>
 *  mensaje --> parsear sobre --> eventId ya procesado? --si--> basicAck (duplicado)
 *                  |                      |no
 *            (JSON invalido)        handler del dominio
 *                  |                 |ok        |RecoverableMessageException   |otra excepcion
 *                  v                 v          v                              v
 *        basicNack(requeue=false)  basicAck   intento < max ?                 basicNack(requeue=false)
 *              -> DLQ                         si: republicar copia con           -> DLQ
 *                                                 x-retry-count+1 y basicAck
 *                                             no: basicNack(requeue=false) -> DLQ
 * </pre>
 */
@Component
public class ManualAckMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(ManualAckMessageProcessor.class);
    public static final String RETRY_HEADER = "x-retry-count";

    private final EnvelopeParser parser;
    private final ProcessedEventRegistry registry;
    private final AckDecisionPolicy policy;
    private final RabbitTemplate rabbitTemplate;
    private final Duration backoff;

    @Autowired
    public ManualAckMessageProcessor(EnvelopeParser parser, ProcessedEventRegistry registry, AckDecisionPolicy policy,
                                     RabbitTemplate rabbitTemplate, NotifyProperties props) {
        this(parser, registry, policy, rabbitTemplate, props.retry().backoff());
    }

    ManualAckMessageProcessor(EnvelopeParser parser, ProcessedEventRegistry registry, AckDecisionPolicy policy,
                              RabbitTemplate rabbitTemplate, Duration backoff) {
        this.parser = parser;
        this.registry = registry;
        this.policy = policy;
        this.rabbitTemplate = rabbitTemplate;
        this.backoff = backoff;
    }

    public void process(Message message, Channel channel, CommandHandler handler) {
        MessageProperties mp = message.getMessageProperties();
        long tag = mp.getDeliveryTag();
        String queue = mp.getConsumerQueue();
        int retryCount = retryCount(mp);
        String eventId = mp.getMessageId();
        String type = mp.getType();

        AckDecision decision;
        String reason = null;
        try {
            EventEnvelope envelope = parser.parse(message);
            eventId = envelope.eventId();
            type = envelope.type();
            if (registry.isProcessed(eventId)) {
                decision = policy.onDuplicate();
            } else {
                handler.handle(envelope, retryCount + 1);
                registry.markProcessed(eventId);
                decision = policy.onSuccess();
            }
        } catch (Exception ex) {
            decision = policy.onFailure(ex, retryCount);
            reason = ex.getClass().getSimpleName() + ": " + ex.getMessage();
        }

        try {
            apply(decision, message, channel, tag, queue, eventId, type, retryCount, reason);
        } catch (IOException | RuntimeException ex) {
            // Si el canal se cerro, RabbitMQ re-entrega el mensaje (sigue unacked) y la idempotencia lo cubre.
            log.error("[ACK-ERROR] no se pudo aplicar {} eventId={} cola={}: {}", decision, eventId, queue, ex.getMessage());
        }
    }

    private void apply(AckDecision decision, Message message, Channel channel, long tag, String queue,
                       String eventId, String type, int retryCount, String reason) throws IOException {
        switch (decision) {
            case ACK -> {
                channel.basicAck(tag, false);
                log.info("[ACK] eventId={} type={} cola={} intento={}", eventId, type, queue, retryCount + 1);
            }
            case ACK_DUPLICATE -> {
                channel.basicAck(tag, false);
                log.warn("[ACK-DUPLICADO] eventId={} type={} cola={} ya fue procesado; se descarta sin reprocesar",
                        eventId, type, queue);
            }
            case RETRY -> retry(message, channel, tag, queue, eventId, type, retryCount, reason);
            case REJECT -> reject(channel, tag, queue, eventId, type, retryCount, reason);
        }
    }

    /**
     * Reintento controlado: se republica una copia en la misma cola (default exchange, routing key =
     * nombre de la cola) con x-retry-count+1 y se hace ACK del original. Asi el contador viaja con
     * el mensaje y no se depende de requeue=true.
     */
    private void retry(Message message, Channel channel, long tag, String queue, String eventId, String type,
                       int retryCount, String reason) throws IOException {
        int nextRetry = retryCount + 1;
        log.warn("[RETRY] eventId={} type={} cola={} intento={}/{} motivo={} -> se reintenta",
                eventId, type, queue, retryCount + 1, policy.maxAttempts(), reason);
        try {
            sleep(backoff.multipliedBy(nextRetry));
            Message copy = MessageBuilder.fromClonedMessage(message)
                    .setHeader(RETRY_HEADER, nextRetry)
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                    .build();
            rabbitTemplate.send(RabbitAdmin.DEFAULT_EXCHANGE_NAME, queue, copy);
            channel.basicAck(tag, false);
        } catch (RuntimeException ex) {
            // Si no se pudo republicar, mejor a la DLQ que perder el mensaje
            reject(channel, tag, queue, eventId, type, retryCount, "fallo al republicar reintento: " + ex.getMessage());
        }
    }

    private void reject(Channel channel, long tag, String queue, String eventId, String type, int retryCount,
                        String reason) throws IOException {
        channel.basicNack(tag, false, false);
        log.error("[NACK->DLQ] eventId={} type={} cola={} intento={}/{} motivo={}",
                eventId, type, queue, retryCount + 1, policy.maxAttempts(), reason);
    }

    static int retryCount(MessageProperties mp) {
        Object value = mp.getHeaders().get(RETRY_HEADER);
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static void sleep(Duration duration) {
        if (duration.isZero() || duration.isNegative()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
