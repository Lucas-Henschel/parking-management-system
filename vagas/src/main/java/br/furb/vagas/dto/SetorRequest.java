package br.furb.vagas.dto;

import br.furb.vagas.enums.CadastroStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetorRequest(
    @NotBlank @Size(max = 30) String codigo,
    @NotNull CadastroStatus status
) {}
