package br.furb.estacionamento.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoConfirmadoPayload(
        UUID pagamentoId,
        UUID ticketId,
        BigDecimal valor,
        String metodo,
        String status
) {
}