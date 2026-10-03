package br.furb.pagamento.dto;

import br.furb.pagamento.entity.Pagamento;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoResponse(
        UUID id,
        UUID ticketId,
        UUID metodoPagamentoId,
        BigDecimal valor,
        LocalDateTime data,
        String status
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
