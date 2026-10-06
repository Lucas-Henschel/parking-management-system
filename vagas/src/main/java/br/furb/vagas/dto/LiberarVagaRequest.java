package br.furb.vagas.dto;

import java.util.UUID;

public record LiberarVagaRequest(UUID ticketId, UUID vagaId) {}
