package br.furb.estacionamento.dto;

import java.util.UUID;

public record VagaResultadoPayload(UUID ticketId, UUID vagaId, String numeroVaga, String motivo) {
}