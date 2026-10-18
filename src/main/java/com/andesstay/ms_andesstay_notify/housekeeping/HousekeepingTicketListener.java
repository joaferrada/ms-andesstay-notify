package com.andesstay.ms_andesstay_notify.housekeeping;

import com.andesstay.ms_andesstay_notify.messaging.ManualAckMessageProcessor;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume q.cmd.housekeeping (llegan por cmd.topic con housekeeping.ticket.preparacion / .limpieza).
 */
@Component
public class HousekeepingTicketListener {

    private final ManualAckMessageProcessor processor;
    private final HousekeepingTicketService ticketService;

    public HousekeepingTicketListener(ManualAckMessageProcessor processor, HousekeepingTicketService ticketService) {
        this.processor = processor;
        this.ticketService = ticketService;
    }

    @RabbitListener(id = "housekeepingListener", queues = "${andesstay.messaging.routes.housekeeping.queue}")
    public void onMessage(Message message, Channel channel) {
        processor.process(message, channel, ticketService::createTicket);
    }
}
