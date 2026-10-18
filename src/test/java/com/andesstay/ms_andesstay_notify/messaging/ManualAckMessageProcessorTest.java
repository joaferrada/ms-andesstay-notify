package com.andesstay.ms_andesstay_notify.messaging;

import com.rabbitmq.client.Channel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ManualAckMessageProcessorTest {

    private static final long TAG = 42L;
    private static final String QUEUE = "q.cmd.email";

    private Channel channel;
    private RabbitTemplate template;
    private ManualAckMessageProcessor processor;
    private FailureSimulator simulator;

    @BeforeEach
    void setUp() {
        channel = mock(Channel.class);
        template = mock(RabbitTemplate.class);
        processor = new ManualAckMessageProcessor(
                new EnvelopeParser(JsonMapper.builder().build()),
                new ProcessedEventRegistry(100),
                new AckDecisionPolicy(3),
                template,
                Duration.ZERO);
        simulator = new FailureSimulator(true);
    }

    @Test
    void exitoHaceBasicAck() throws Exception {
        processor.process(msg(EnvelopeParserTest.VALID, 0), channel, (env, attempt) -> { });

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void duplicadoHaceAckSinReprocesar() throws Exception {
        AtomicInteger ejecuciones = new AtomicInteger();
        CommandHandler handler = (env, attempt) -> ejecuciones.incrementAndGet();

        processor.process(msg(EnvelopeParserTest.VALID, 0), channel, handler);
        processor.process(msg(EnvelopeParserTest.VALID, 0), channel, handler);

        assertThat(ejecuciones.get()).isEqualTo(1);
        verify(channel, times(2)).basicAck(TAG, false);
    }

    @Test
    void jsonInvalidoHaceNackSinRequeue() throws Exception {
        processor.process(msg("{roto", 0), channel, (env, attempt) -> { });

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void fatalHaceNackSinRequeue() throws Exception {
        String body = EnvelopeParserTest.VALID.replace("\"clienteUsername\":\"ana\"", "\"clienteUsername\":\"ana\",\"simulateFailure\":\"FATAL\"");
        processor.process(msg(body, 0), channel, simulator::apply);

        verify(channel).basicNack(TAG, false, false);
        verifyNoInteractions(template);
    }

    @Test
    void transitorioRepublicaConContadorYHaceAckDelOriginal() throws Exception {
        String body = EnvelopeParserTest.VALID.replace("\"clienteUsername\":\"ana\"", "\"clienteUsername\":\"ana\",\"simulateFailure\":\"TRANSIENT\"");
        processor.process(msg(body, 1), channel, simulator::apply);

        ArgumentCaptor<Message> copia = ArgumentCaptor.forClass(Message.class);
        verify(template).send(eq(""), eq(QUEUE), copia.capture());
        assertThat((Integer) copia.getValue().getMessageProperties().getHeader(ManualAckMessageProcessor.RETRY_HEADER)).isEqualTo(2);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void transitorioEnUltimoIntentoVaADlq() throws Exception {
        String body = EnvelopeParserTest.VALID.replace("\"clienteUsername\":\"ana\"", "\"clienteUsername\":\"ana\",\"simulateFailure\":\"TRANSIENT\"");
        processor.process(msg(body, 2), channel, simulator::apply);

        verify(channel).basicNack(TAG, false, false);
        verify(template, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void transitorioConFailTimesSeRecupera() throws Exception {
        String body = EnvelopeParserTest.VALID.replace("\"clienteUsername\":\"ana\"",
                "\"clienteUsername\":\"ana\",\"simulateFailure\":\"TRANSIENT\",\"failTimes\":1");
        processor.process(msg(body, 1), channel, simulator::apply); // intento 2 > failTimes

        verify(channel).basicAck(TAG, false);
        verifyNoInteractions(template);
    }

    private static Message msg(String body, int retryCount) {
        MessageProperties mp = new MessageProperties();
        mp.setDeliveryTag(TAG);
        mp.setConsumerQueue(QUEUE);
        if (retryCount > 0) {
            mp.setHeader(ManualAckMessageProcessor.RETRY_HEADER, retryCount);
        }
        return new Message(body.getBytes(StandardCharsets.UTF_8), mp);
    }
}
