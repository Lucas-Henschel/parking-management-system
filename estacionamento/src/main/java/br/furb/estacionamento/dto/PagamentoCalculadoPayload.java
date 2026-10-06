package br.furb.estacionamento.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoCalculadoPayload(UUID pagamentoId, UUID ticketId, BigDecimal valor, String status) {
}