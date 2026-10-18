package com.andesstay.ms_andesstay_notify.email;

import com.andesstay.ms_andesstay_notify.messaging.ManualAckMessageProcessor;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume q.cmd.email (nombre desde andesstay.messaging.routes.email.queue) con ACK manual.
 */
@Component
public class EmailCommandListener {

    private final ManualAckMessageProcessor processor;
    private final EmailSender emailSender;

    public EmailCommandListener(ManualAckMessageProcessor processor, EmailSender emailSender) {
        this.processor = processor;
        this.emailSender = emailSender;
    }

    @RabbitListener(id = "emailListener", queues = "${andesstay.messaging.routes.email.queue}")
    public void onMessage(Message message, Channel channel) {
        processor.process(message, channel, emailSender::send);
    }
}
