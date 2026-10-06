package br.furb.estacionamento.dto;

import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.enums.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TicketResponse(
        UUID id,
        UUID veiculoId,
        String placa,
        UUID vagaId,
        Instant entrada,
        Instant saida,
        TicketStatus status,
        BigDecimal valor,
        int tentativasReserva
) {
    public static TicketResponse from(Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getVeiculo().getId(),
                ticket.getVeiculo().getPlaca(),
                ticket.getVagaId(),
                ticket.getEntrada(),
                ticket.getSaida(),
                ticket.getStatus(),
                ticket.getValor(),
                ticket.getTentativasReserva()
        );
    }
}
