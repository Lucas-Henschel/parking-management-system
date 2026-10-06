package br.furb.estacionamento.dto;

import br.furb.estacionamento.enums.TipoMensagem;

import java.time.Instant;
import java.util.UUID;

public record MensagemEnvelope<T>(
    UUID messageId,
    UUID correlationId,
    TipoMensagem tipo,
    Instant timestamp,
    T payload
) {
}