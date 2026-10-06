package br.furb.estacionamento.dto;

import java.util.UUID;

public record LiberarVagaPayload(UUID ticketId, UUID vagaId) {
}