package br.furb.vagas.dto;

import br.furb.vagas.enums.MotivoIndisponibilidade;

import java.util.UUID;

public record VagaIndisponivelEvent(UUID ticketId, MotivoIndisponibilidade motivo) {}
