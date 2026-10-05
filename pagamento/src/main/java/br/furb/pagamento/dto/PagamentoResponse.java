package br.furb.pagamento.dto;

import br.furb.pagamento.entity.Pagamento;
import br.furb.pagamento.enums.PagamentoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PagamentoResponse(
    UUID id,
    UUID ticketId,
    UUID metodoPagamentoId,
    BigDecimal valor,
    Instant data,
    PagamentoStatus status
) {
    public static PagamentoResponse from(Pagamento pagamento) {
        return new PagamentoResponse(
            pagamento.getId(),
            pagamento.getTicketId(),
            pagamento.getMetodoPagamentoId(),
            pagamento.getValor(),
            pagamento.getData(),
            pagamento.getStatus()
        );
    }
}
