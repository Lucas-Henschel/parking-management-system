package br.furb.estacionamento.dto;

import br.furb.estacionamento.enums.MotivoIndisponibilidade;

import java.util.UUID;

public record VagaResultadoPayload(UUID ticketId, UUID vagaId, String numeroVaga, MotivoIndisponibilidade motivo) {
}