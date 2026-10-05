package br.furb.vagas.dto;

import br.furb.vagas.enums.TipoMensagem;

import java.time.Instant;
import java.util.UUID;

public record MensagemEnvelope<T>(
    UUID messageId,
    UUID correlationId,
    TipoMensagem tipo,
    Instant timestamp,
    T payload
) {}
