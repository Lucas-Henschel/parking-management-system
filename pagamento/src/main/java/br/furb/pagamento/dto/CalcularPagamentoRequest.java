package br.furb.pagamento.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CalcularPagamentoRequest(
        @NotNull UUID ticketId,
        @NotNull LocalDateTime entrada,
        @NotNull LocalDateTime saida,
        @NotNull UUID metodoPagamentoId
) {
}
