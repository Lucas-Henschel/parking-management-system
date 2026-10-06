package br.furb.vagas.dto;

import java.util.UUID;

public record VagaReservadaEvent(UUID ticketId, UUID vagaId, String numeroVaga) {}
