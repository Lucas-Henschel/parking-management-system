package br.furb.estacionamento.dto;

import br.furb.estacionamento.enums.PagamentoStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoConfirmadoPayload(
    UUID pagamentoId,
    UUID ticketId,
    BigDecimal valor,
    String metodo,
    PagamentoStatus status
) {
}