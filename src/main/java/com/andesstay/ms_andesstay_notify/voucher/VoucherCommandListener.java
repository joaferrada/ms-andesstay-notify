package com.andesstay.ms_andesstay_notify.voucher;

import com.andesstay.ms_andesstay_notify.messaging.ManualAckMessageProcessor;
import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consume q.cmd.voucher (vouchers de confirmacion y boletas de checkout).
 */
@Component
public class VoucherCommandListener {

    private final ManualAckMessageProcessor processor;
    private final VoucherGenerator voucherGenerator;

    public VoucherCommandListener(ManualAckMessageProcessor processor, VoucherGenerator voucherGenerator) {
        this.processor = processor;
        this.voucherGenerator = voucherGenerator;
    }

    @RabbitListener(id = "voucherListener", queues = "${andesstay.messaging.routes.voucher.queue}")
    public void onMessage(Message message, Channel channel) {
        processor.process(message, channel, voucherGenerator::generate);
    }
}
