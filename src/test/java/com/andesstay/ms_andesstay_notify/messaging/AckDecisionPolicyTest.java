package com.andesstay.ms_andesstay_notify.messaging;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AckDecisionPolicyTest {

    private final AckDecisionPolicy policy = new AckDecisionPolicy(3);

    @Test
    void exitoEsAck() {
        assertThat(policy.onSuccess()).isEqualTo(AckDecision.ACK);
    }

    @Test
    void duplicadoEsAckSinReprocesar() {
        assertThat(policy.onDuplicate()).isEqualTo(AckDecision.ACK_DUPLICATE);
    }

    @Test
    void noRecuperableVaDirectoADlq() {
        assertThat(policy.onFailure(new NonRecoverableMessageException("JSON invalido"), 0)).isEqualTo(AckDecision.REJECT);
    }

    @Test
    void excepcionInesperadaSeTrataComoNoRecuperable() {
        assertThat(policy.onFailure(new NullPointerException(), 0)).isEqualTo(AckDecision.REJECT);
    }

    @Test
    void recuperableReintentaHastaMaxAttemptsYLuegoDlq() {
        RecoverableMessageException transitorio = new RecoverableMessageException("timeout");
        assertThat(policy.onFailure(transitorio, 0)).isEqualTo(AckDecision.RETRY);  // intento 1
        assertThat(policy.onFailure(transitorio, 1)).isEqualTo(AckDecision.RETRY);  // intento 2
        assertThat(policy.onFailure(transitorio, 2)).isEqualTo(AckDecision.REJECT); // intento 3 = max
        assertThat(policy.onFailure(transitorio, 7)).isEqualTo(AckDecision.REJECT);
    }
}
