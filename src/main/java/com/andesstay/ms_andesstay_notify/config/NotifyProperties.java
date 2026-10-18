package com.andesstay.ms_andesstay_notify.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Parametros del consumidor ({@code andesstay.notify.*}).
 */
@Validated
@ConfigurationProperties(prefix = "andesstay.notify")
public record NotifyProperties(
        @Valid @NotNull Retry retry,
        @Valid @NotNull Idempotency idempotency,
        @Valid @NotNull Simulation simulation,
        @Valid @NotNull DeadLetter deadLetter) {

    /**
     * @param maxAttempts intentos totales antes de enviar a la DLQ (incluye el primero)
     * @param backoff     espera base antes de republicar un reintento
     */
    public record Retry(@Min(1) int maxAttempts, @NotNull Duration backoff) {
    }

    public record Idempotency(@Min(1) int maxEntries) {
    }

    public record Simulation(boolean enabled) {
    }

    public record DeadLetter(boolean autoStartup) {
    }
}
