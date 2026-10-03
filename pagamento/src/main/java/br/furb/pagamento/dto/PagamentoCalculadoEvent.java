package br.furb.pagamento.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PagamentoCalculadoEvent(
        UUID pagamentoId,
        UUID ticketId,
        BigDecimal valor,
        LocalDateTime data,
        String status
) {
}
