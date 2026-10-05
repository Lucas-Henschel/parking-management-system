package br.furb.pagamento.dto;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CalcularPagamentoRequest(
    @NotNull UUID ticketId,
    @NotNull Instant entrada,
    @NotNull Instant saida
) {}
