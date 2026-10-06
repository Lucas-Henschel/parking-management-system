package br.furb.estacionamento.dto;

import br.furb.estacionamento.enums.PagamentoStatus;

import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoCalculadoPayload(UUID pagamentoId, UUID ticketId, BigDecimal valor, PagamentoStatus status) {
}