package com.andesstay.ms_andesstay_notify.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Nombres de exchanges, colas y routing keys ({@code andesstay.messaging.*} en application.yaml).
 * Deben coincidir con los de ms-andesstay-reservations: ambos declaran la misma topologia.
 */
@Validated
@ConfigurationProperties(prefix = "andesstay.messaging")
public record MessagingProperties(
        @NotNull Duration dlqRetention,
        @Valid @NotNull Exchanges exchanges,
        @Valid @NotNull Routes routes) {

    public record Exchanges(
            @NotBlank String direct,
            @NotBlank String topic,
            @NotBlank String deadLetter) {
    }

    public record Routes(
            @Valid @NotNull Route email,
            @Valid @NotNull Route housekeeping,
            @Valid @NotNull Route voucher) {
    }

    /**
     * @param directKey    binding con cmd.direct
     * @param topicPattern binding con cmd.topic
     * @param deadKey      x-dead-letter-routing-key de la cola y binding de la DLQ con cmd.dead.dlx
     */
    public record Route(
            @NotBlank String queue,
            @NotBlank String dlq,
            @NotBlank String directKey,
            @NotBlank String topicPattern,
            @NotBlank String deadKey) {
    }
}
