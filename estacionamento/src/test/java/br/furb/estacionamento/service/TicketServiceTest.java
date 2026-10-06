package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.repository.TicketRepository;
import br.furb.estacionamento.repository.VeiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import br.furb.estacionamento.exception.ConflitoNegocioException;

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
    private TicketLocalizador localizador;

    @Mock
    private RegistroEventoService registroEvento;

    private TicketService ticketService;

    @BeforeEach
    void setUp() {
        ticketService = new TicketService(ticketRepository, veiculoRepository, localizador, registroEvento);
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
        verify(registroEvento).registrarReserva(ticketCaptor.getValue().getId());
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

        ConflitoNegocioException exception = assertThrows(
                ConflitoNegocioException.class,
                () -> ticketService.registrarEntrada("ABC1234")
        );

        assertEquals("O veículo já possui um ticket em aberto.", exception.getMessage());
        verify(ticketRepository, never()).save(any(Ticket.class));
    }

    @Test
    void deveRegistrarSaidaEPedirCalculoDePagamento() {
        Veiculo veiculo = new Veiculo("ABC1234");
        Ticket ticket = new Ticket(veiculo, Instant.now().minusSeconds(3600));
        ticket.confirmarVaga(UUID.randomUUID());
        UUID ticketId = UUID.randomUUID();
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        when(localizador.buscarParaAlteracao(ticketId)).thenReturn(ticket);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse saida = ticketService.registrarSaida(ticketId);

        assertEquals(TicketStatus.ATIVO, saida.status());
        verify(registroEvento).registrarCalculoPagamento(any());
    }
}
