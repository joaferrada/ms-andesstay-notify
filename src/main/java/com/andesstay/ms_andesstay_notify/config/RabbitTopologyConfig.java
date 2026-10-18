package com.andesstay.ms_andesstay_notify.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topologia RabbitMQ de AndesStay. RabbitAdmin (autoconfigurado por Spring Boot) declara
 * todos estos beans en el broker la primera vez que se abre una conexion. Declarar es
 * idempotente: reservations declara exactamente la misma topologia con los mismos argumentos,
 * asi el consumidor puede arrancar antes que el productor.
 *
 * <pre>
 *                     email.send                      +--------------+  email.dead        +------------------+
 *  [cmd.direct] ----------------------------------->  | q.cmd.email  | ----------------> | q.cmd.email.dlq  |
 *  [cmd.topic]  ---- email.*  ----------------------> |              |   (via DLX)       +------------------+
 *                                                     +--------------+
 *                     housekeeping.ticket             +--------------------+  housekeeping.dead  +------------------------+
 *  [cmd.direct] ----------------------------------->  | q.cmd.housekeeping | ----------------> | q.cmd.housekeeping.dlq |
 *  [cmd.topic]  ---- housekeeping.# ----------------> |                    |                   +------------------------+
 *                                                     +--------------------+
 *                     voucher.gen                     +----------------+  voucher.dead       +--------------------+
 *  [cmd.direct] ----------------------------------->  | q.cmd.voucher  | ----------------> | q.cmd.voucher.dlq  |
 *  [cmd.topic]  ---- voucher.* ---------------------> |                |                   +--------------------+
 *                                                     +----------------+
 *  Las flechas "dead" pasan por [cmd.dead.dlx] (direct). Las DLQ retienen mensajes por x-message-ttl (7 dias).
 * </pre>
 */
@Configuration
public class RabbitTopologyConfig {

    static final String ARG_DLX = "x-dead-letter-exchange";
    static final String ARG_DLK = "x-dead-letter-routing-key";
    static final String ARG_TTL = "x-message-ttl";

    private final MessagingProperties props;

    public RabbitTopologyConfig(MessagingProperties props) {
        this.props = props;
    }

    // ---------------------------------------------------------------- Exchanges

    /** cmd.direct: match exacto de routing key. Se usa para email.send y voucher.gen. */
    @Bean
    public DirectExchange commandDirectExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().direct()).durable(true).build();
    }

    /** cmd.topic: match por patron. Se usa para housekeeping.ticket.preparacion/limpieza. */
    @Bean
    public TopicExchange commandTopicExchange() {
        return ExchangeBuilder.topicExchange(props.exchanges().topic()).durable(true).build();
    }

    /** cmd.dead.dlx: Dead Letter Exchange. Recibe lo que las colas principales rechazan. */
    @Bean
    public DirectExchange deadLetterExchange() {
        return ExchangeBuilder.directExchange(props.exchanges().deadLetter()).durable(true).build();
    }

    // ---------------------------------------------------------------- EMAIL

    /** q.cmd.email: comandos de envio de correo. Si se rechaza -> cmd.dead.dlx con key email.dead. */
    @Bean
    public Queue emailQueue() {
        return mainQueue(props.routes().email());
    }

    /** q.cmd.email.dlq: correos que no se pudieron procesar. */
    @Bean
    public Queue emailDlq() {
        return deadLetterQueue(props.routes().email());
    }

    /** cmd.direct --[email.send]--> q.cmd.email */
    @Bean
    public Binding emailDirectBinding() {
        return BindingBuilder.bind(emailQueue()).to(commandDirectExchange()).with(props.routes().email().directKey());
    }

    /** cmd.topic --[email.*]--> q.cmd.email */
    @Bean
    public Binding emailTopicBinding() {
        return BindingBuilder.bind(emailQueue()).to(commandTopicExchange()).with(props.routes().email().topicPattern());
    }

    /** cmd.dead.dlx --[email.dead]--> q.cmd.email.dlq */
    @Bean
    public Binding emailDeadLetterBinding() {
        return BindingBuilder.bind(emailDlq()).to(deadLetterExchange()).with(props.routes().email().deadKey());
    }

    // ---------------------------------------------------------------- HOUSEKEEPING

    /** q.cmd.housekeeping: tickets de preparacion/limpieza de habitaciones. */
    @Bean
    public Queue housekeepingQueue() {
        return mainQueue(props.routes().housekeeping());
    }

    /** q.cmd.housekeeping.dlq */
    @Bean
    public Queue housekeepingDlq() {
        return deadLetterQueue(props.routes().housekeeping());
    }

    /** cmd.direct --[housekeeping.ticket]--> q.cmd.housekeeping */
    @Bean
    public Binding housekeepingDirectBinding() {
        return BindingBuilder.bind(housekeepingQueue()).to(commandDirectExchange()).with(props.routes().housekeeping().directKey());
    }

    /** cmd.topic --[housekeeping.#]--> q.cmd.housekeeping (calza housekeeping.ticket.preparacion, .limpieza, etc.) */
    @Bean
    public Binding housekeepingTopicBinding() {
        return BindingBuilder.bind(housekeepingQueue()).to(commandTopicExchange()).with(props.routes().housekeeping().topicPattern());
    }

    /** cmd.dead.dlx --[housekeeping.dead]--> q.cmd.housekeeping.dlq */
    @Bean
    public Binding housekeepingDeadLetterBinding() {
        return BindingBuilder.bind(housekeepingDlq()).to(deadLetterExchange()).with(props.routes().housekeeping().deadKey());
    }

    // ---------------------------------------------------------------- VOUCHER

    /** q.cmd.voucher: generacion de vouchers y boletas (PDF simulado). */
    @Bean
    public Queue voucherQueue() {
        return mainQueue(props.routes().voucher());
    }

    /** q.cmd.voucher.dlq */
    @Bean
    public Queue voucherDlq() {
        return deadLetterQueue(props.routes().voucher());
    }

    /** cmd.direct --[voucher.gen]--> q.cmd.voucher */
    @Bean
    public Binding voucherDirectBinding() {
        return BindingBuilder.bind(voucherQueue()).to(commandDirectExchange()).with(props.routes().voucher().directKey());
    }

    /** cmd.topic --[voucher.*]--> q.cmd.voucher */
    @Bean
    public Binding voucherTopicBinding() {
        return BindingBuilder.bind(voucherQueue()).to(commandTopicExchange()).with(props.routes().voucher().topicPattern());
    }

    /** cmd.dead.dlx --[voucher.dead]--> q.cmd.voucher.dlq */
    @Bean
    public Binding voucherDeadLetterBinding() {
        return BindingBuilder.bind(voucherDlq()).to(deadLetterExchange()).with(props.routes().voucher().deadKey());
    }

    // ---------------------------------------------------------------- helpers

    /** Cola principal durable con su DLX y dead-letter routing key. */
    private Queue mainQueue(MessagingProperties.Route route) {
        return QueueBuilder.durable(route.queue())
                .withArgument(ARG_DLX, props.exchanges().deadLetter())
                .withArgument(ARG_DLK, route.deadKey())
                .build();
    }

    /** DLQ durable con TTL de retencion (por defecto 7 dias). */
    private Queue deadLetterQueue(MessagingProperties.Route route) {
        return QueueBuilder.durable(route.dlq())
                .withArgument(ARG_TTL, props.dlqRetention().toMillis())
                .build();
    }
}
