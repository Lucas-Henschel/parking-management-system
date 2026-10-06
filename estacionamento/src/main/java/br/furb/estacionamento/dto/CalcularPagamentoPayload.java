package br.furb.estacionamento.dto;

import java.time.Instant;
import java.util.UUID;

public record CalcularPagamentoPayload(UUID ticketId, Instant entrada, Instant saida) {
}