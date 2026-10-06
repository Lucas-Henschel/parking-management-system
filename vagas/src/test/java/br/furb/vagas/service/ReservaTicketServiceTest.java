package br.furb.vagas.service;

import br.furb.vagas.entity.ReservaTicket;
import br.furb.vagas.repository.ReservaTicketRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReservaTicketServiceTest {
    private final ReservaTicketRepository repository = mock(ReservaTicketRepository.class);
    private final ReservaTicketService service = new ReservaTicketService(repository);
    private final UUID ticketId = UUID.randomUUID();

    @Test
    void deveCriarAReservaPendenteAntesDeTravar() {
        ReservaTicket reserva = new ReservaTicket(ticketId);
        when(repository.buscarParaAlteracao(ticketId)).thenReturn(Optional.of(reserva));

        assertThat(service.travar(ticketId)).isSameAs(reserva);

        var ordem = inOrder(repository);
        ordem.verify(repository).criarPendenteSeAusente(ticketId);
        ordem.verify(repository).buscarParaAlteracao(ticketId);
    }

    @Test
    void deveFalharSeAReservaNaoExisteAposACriacao() {
        when(repository.buscarParaAlteracao(ticketId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.travar(ticketId)).isInstanceOf(IllegalStateException.class);
    }
}
