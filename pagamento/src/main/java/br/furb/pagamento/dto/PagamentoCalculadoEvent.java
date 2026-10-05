package br.furb.pagamento.dto;

import br.furb.pagamento.enums.PagamentoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PagamentoCalculadoEvent(
    UUID pagamentoId,
    UUID ticketId,
    BigDecimal valor,
    Instant data,
    PagamentoStatus status
) {}
