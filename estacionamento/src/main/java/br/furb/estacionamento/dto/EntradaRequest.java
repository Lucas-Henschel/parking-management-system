package br.furb.estacionamento.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EntradaRequest(
        @NotBlank
        @Size(max = 10)
        String placa
) {
}