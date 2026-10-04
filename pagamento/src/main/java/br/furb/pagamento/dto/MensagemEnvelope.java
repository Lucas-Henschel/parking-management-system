package br.furb.pagamento.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MensagemEnvelope<T>(
        UUID messageId,
        UUID correlationId,
        String tipo,
        LocalDateTime timestamp,
        T payload
) {}
