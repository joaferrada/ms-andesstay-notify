package com.andesstay.ms_andesstay_notify.deadletter;

import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Inspecciona las DLQ y loguea el header x-death que agrega RabbitMQ al hacer dead-lettering
 * (motivo, cola de origen, exchange, routing keys, cantidad de veces).
 *
 * autoStartup configurable (andesstay.notify.dead-letter.auto-startup, false por defecto):
 * apagado, los mensajes quedan en la DLQ como evidencia en la Management UI; encendido,
 * se loguean y se hace ACK (se "archivan" en el log y salen de la DLQ).
 */
@Component
public class DeadLetterListener {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterListener.class);
    private static final int MAX_BODY_LOG = 500;

    @RabbitListener(id = "deadLetterListener",
            queues = {
                    "${andesstay.messaging.routes.email.dlq}",
                    "${andesstay.messaging.routes.housekeeping.dlq}",
                    "${andesstay.messaging.routes.voucher.dlq}"
            },
            autoStartup = "${andesstay.notify.dead-letter.auto-startup:false}")
    public void onDeadLetter(Message message, Channel channel) throws IOException {
        MessageProperties mp = message.getMessageProperties();
        List<Map<String, ?>> xDeath = mp.getXDeathHeader();

        log.error("[DLQ] cola={} messageId={} type={} x-retry-count={}",
                mp.getConsumerQueue(), mp.getMessageId(), mp.getType(), mp.getHeaders().get("x-retry-count"));
        if (xDeath != null) {
            for (Map<String, ?> death : xDeath) {
                log.error("[DLQ]   x-death reason={} queue={} exchange={} routing-keys={} count={} time={}",
                        death.get("reason"), death.get("queue"), death.get("exchange"),
                        death.get("routing-keys"), death.get("count"), death.get("time"));
            }
        }
        log.error("[DLQ]   body={}", abbreviate(new String(message.getBody(), StandardCharsets.UTF_8)));

        channel.basicAck(mp.getDeliveryTag(), false);
    }

    private static String abbreviate(String body) {
        return body.length() <= MAX_BODY_LOG ? body : body.substring(0, MAX_BODY_LOG) + "...";
    }
}
