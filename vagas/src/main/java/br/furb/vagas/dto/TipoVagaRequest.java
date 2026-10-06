package br.furb.vagas.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TipoVagaRequest(@NotBlank @Size(max = 80) String nome) {}
