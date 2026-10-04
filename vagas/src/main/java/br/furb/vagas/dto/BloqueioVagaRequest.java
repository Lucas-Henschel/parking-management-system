package br.furb.vagas.dto;

import jakarta.validation.constraints.NotNull;

public record BloqueioVagaRequest(@NotNull Boolean bloqueada) {}
