package br.furb.estacionamento.service;

import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.enums.Recurso;
import br.furb.estacionamento.exception.RecursoNaoEncontradoException;
import br.furb.estacionamento.repository.TicketRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class TicketLocalizador {
    private final TicketRepository ticketRepository;

    public TicketLocalizador(TicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    public Ticket buscarParaAlteracao(UUID ticketId) {
        return ticketRepository.buscarParaAlteracao(ticketId)
            .orElseThrow(() -> new RecursoNaoEncontradoException(Recurso.TICKET));
    }
}
