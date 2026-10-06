package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.messaging.TicketPublisher;
import br.furb.estacionamento.repository.TicketRepository;
import br.furb.estacionamento.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketServiceTest {

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private VeiculoRepository veiculoRepository;

    @Mock
    private TicketPublisher ticketPublisher;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, veiculoRepository, ticketPublisher);
    }

    @Test
    void deveNormalizarPlacaECriarTicketPendente() {
        Veiculo veiculo = new Veiculo("ABC1234");
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.empty());
        when(veiculoRepository.save(any(Veiculo.class))).thenReturn(veiculo);
        when(ticketRepository.existsByVeiculo_IdAndStatusIn(nullable(UUID.class), anyList())).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.registrarEntrada(" abc1234 ");

        ArgumentCaptor<Ticket> ticketCaptor = ArgumentCaptor.forClass(Ticket.class);
        verify(ticketRepository).save(ticketCaptor.capture());
        assertEquals("ABC1234", veiculo.getPlaca());
        assertEquals(TicketStatus.PENDENTE, ticketCaptor.getValue().getStatus());
        assertEquals(veiculo, ticketCaptor.getValue().getVeiculo());
        assertEquals("ABC1234", response.placa());
        verify(ticketPublisher).publicarReserva(ticketCaptor.getValue().getId());
    }

    @Test
    void deveReutilizarVeiculoExistente() {
        Veiculo veiculo = new Veiculo("ABC1234");
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));
        when(ticketRepository.existsByVeiculo_IdAndStatusIn(nullable(UUID.class), anyList())).thenReturn(false);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ticketService.registrarEntrada("ABC1234");

        verify(veiculoRepository, never()).save(any(Veiculo.class));
        verify(ticketRepository).save(any(Ticket.class));
    }

    @Test
    void deveRejeitarEntradaComTicketEmAberto() {
        Veiculo veiculo = new Veiculo("ABC1234");
        when(veiculoRepository.findByPlaca("ABC1234")).thenReturn(Optional.of(veiculo));
        when(ticketRepository.existsByVeiculo_IdAndStatusIn(nullable(UUID.class), anyList())).thenReturn(true);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> ticketService.registrarEntrada("ABC1234")
        );

        assertEquals(409, exception.getStatusCode().value());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void deveAtivarTicketQuandoVagaForConfirmada() {
        Veiculo veiculo = new Veiculo("ABC1234");
        Ticket ticket = new Ticket(veiculo, Instant.now());
        UUID ticketId = UUID.randomUUID();
        UUID vagaId = UUID.randomUUID();
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = ticketService.registrarVagaConfirmada(ticketId, vagaId);

        assertEquals(TicketStatus.ATIVO, response.status());
        assertEquals(vagaId, response.vagaId());
    }

    @Test
    void devePercorrerSaidaPagamentoEFinalizacao() {
        Veiculo veiculo = new Veiculo("ABC1234");
        Ticket ticket = new Ticket(veiculo, Instant.now().minusSeconds(3600));
        ticket.setStatus(TicketStatus.ATIVO);
        ticket.setVagaId(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        when(ticketRepository.findByIdForUpdate(ticketId)).thenReturn(Optional.of(ticket));
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse saida = ticketService.registrarSaida(ticketId);
        UUID pagamentoId = UUID.randomUUID();
        TicketResponse calculado = ticketService.registrarPagamentoCalculado(ticketId, pagamentoId, new BigDecimal("20.00"));
        TicketResponse finalizado = ticketService.confirmarPagamento(ticketId, pagamentoId, new BigDecimal("20.00"));

        assertEquals(TicketStatus.ATIVO, saida.status());
        assertEquals(TicketStatus.AGUARDANDO_PAGAMENTO, calculado.status());
        assertEquals(new BigDecimal("20.00"), calculado.valor());
        assertEquals(TicketStatus.FINALIZADO, finalizado.status());
        verify(ticketPublisher).publicarCalculoPagamento(any());
        verify(ticketPublisher).publicarLiberacao(ticketId, ticket.getVagaId());
    }
}
