package com.andesstay.ms_andesstay_notify.messaging;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnvelopeParserTest {

    static final String VALID = """
            {"type":"EMAIL_RESERVA_CREADA","eventId":"4f9a2c1e-1111-4a6b-9c1d-2e3f4a5b6c7d",
             "timestamp":"2026-10-18T12:00:00Z","traceId":"t-1","correlationId":"reservation-1",
             "source":"ms-andesstay-reservations","payload":{"reservationId":1,"clienteUsername":"ana"}}
            """;

    private final EnvelopeParser parser = new EnvelopeParser(JsonMapper.builder().build());

    @Test
    void parseaSobreValido() {
        EventEnvelope env = parser.parse(msg(VALID));
        assertThat(env.type()).isEqualTo("EMAIL_RESERVA_CREADA");
        assertThat(env.requirePayload("reservationId")).isEqualTo("1");
    }

    @Test
    void jsonInvalidoEsNoRecuperable() {
        assertThatThrownBy(() -> parser.parse(msg("{esto no es json")))
                .isInstanceOf(NonRecoverableMessageException.class)
                .hasMessageContaining("JSON invalido");
    }

    @Test
    void faltaEventIdEsNoRecuperable() {
        assertThatThrownBy(() -> parser.parse(msg(VALID.replace("\"eventId\":\"4f9a2c1e-1111-4a6b-9c1d-2e3f4a5b6c7d\",", ""))))
                .isInstanceOf(NonRecoverableMessageException.class)
                .hasMessageContaining("eventId");
    }

    @Test
    void eventIdQueNoEsUuidEsNoRecuperable() {
        assertThatThrownBy(() -> parser.parse(msg(VALID.replace("4f9a2c1e-1111-4a6b-9c1d-2e3f4a5b6c7d", "abc"))))
                .isInstanceOf(NonRecoverableMessageException.class);
    }

    @Test
    void campoDePayloadFaltanteEsNoRecuperable() {
        EventEnvelope env = parser.parse(msg(VALID));
        assertThatThrownBy(() -> env.requirePayload("alojamientoNombre"))
                .isInstanceOf(NonRecoverableMessageException.class)
                .hasMessageContaining("payload.alojamientoNombre");
    }

    static Message msg(String body) {
        return new Message(body.getBytes(StandardCharsets.UTF_8), new MessageProperties());
    }
}
