package br.furb.vagas.service;

import br.furb.vagas.entity.ReservaTicket;
import br.furb.vagas.repository.ReservaTicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ReservaTicketService {
    private final ReservaTicketRepository reservaTicketRepository;

    public ReservaTicketService(ReservaTicketRepository reservaTicketRepository) {
        this.reservaTicketRepository = reservaTicketRepository;
    }

    /**
     * Garante a reserva do ticket e a trava para alteração, serializando mensagens diferentes do
     * mesmo ticket. O lock só vale dentro da transação do chamador, por isso ela é obrigatória.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReservaTicket travar(UUID ticketId) {
        reservaTicketRepository.criarPendenteSeAusente(ticketId);

        return reservaTicketRepository.buscarParaAlteracao(ticketId)
            .orElseThrow(() -> new IllegalStateException("Reserva do ticket não encontrada após criação"));
    }
}
