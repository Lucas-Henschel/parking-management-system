package br.furb.vagas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record VagaRequest(
        @NotBlank @Size(max = 30) String numero,
        @NotNull UUID blocoId,
        @NotNull UUID tipoId) {}
