package br.furb.pagamento.dto;

import java.time.Instant;
import java.util.UUID;

public record MensagemEnvelope<T>(
    UUID messageId,
    UUID correlationId,
    String tipo,
    Instant timestamp,
    T payload
) {}
