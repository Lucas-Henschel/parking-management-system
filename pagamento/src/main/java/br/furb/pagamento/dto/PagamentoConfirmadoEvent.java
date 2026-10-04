package br.furb.pagamento.dto;

import br.furb.pagamento.entity.PagamentoStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record PagamentoConfirmadoEvent(
        UUID pagamentoId,
        UUID ticketId,
        BigDecimal valor,
        String metodo,
        PagamentoStatus status
) {}
