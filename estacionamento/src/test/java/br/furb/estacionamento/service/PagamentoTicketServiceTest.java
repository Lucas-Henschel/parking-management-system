package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.PagamentoCalculadoPayload;
import br.furb.estacionamento.dto.PagamentoConfirmadoPayload;
import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.PagamentoStatus;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.enums.TipoMensagem;
import br.furb.estacionamento.repository.MensagemProcessadaRepository;
import br.furb.estacionamento.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PagamentoTicketServiceTest {

    @Mock
    private MensagemProcessadaRepository mensagemProcessadaRepository;

    @Mock
    private TicketLocalizador localizador;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RegistroEventoService registroEvento;

    private PagamentoTicketService service;

    @BeforeEach
    void setUp() {
        service = new PagamentoTicketService(
                new MensagemValidador(), mensagemProcessadaRepository, localizador, ticketRepository, registroEvento);
    }

    @Test
    void ignoraMensagemDePagamentoJaProcessada() {
        UUID ticketId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MensagemEnvelope<PagamentoCalculadoPayload> envelope = envelope(
                messageId, ticketId, TipoMensagem.PAGAMENTO_CALCULADO,
                new PagamentoCalculadoPayload(UUID.randomUUID(), ticketId, new BigDecimal("20.00"), PagamentoStatus.CALCULADO));
        when(mensagemProcessadaRepository.registrarSeAusente(eq(messageId), any(Instant.class))).thenReturn(0);

        service.processarPagamentoCalculado(envelope);

        verify(localizador, never()).buscarParaAlteracao(any());
    }

    @Test
    void rejeitaEnvelopeComCorrelationIdDiferenteDoTicket() {
        UUID ticketId = UUID.randomUUID();
        MensagemEnvelope<PagamentoConfirmadoPayload> envelope = envelope(
                UUID.randomUUID(), UUID.randomUUID(), TipoMensagem.PAGAMENTO_CONFIRMADO,
                new PagamentoConfirmadoPayload(UUID.randomUUID(), ticketId, new BigDecimal("20.00"), "PIX", PagamentoStatus.PAGO));

        assertThrows(IllegalArgumentException.class, () -> service.processarPagamentoConfirmado(envelope));
        verify(mensagemProcessadaRepository, never()).registrarSeAusente(any(), any());
    }

    @Test
    void devePercorrerPagamentoEFinalizacao() {
        Ticket ticket = new Ticket(new Veiculo("ABC1234"), Instant.now().minusSeconds(3600));
        ticket.confirmarVaga(UUID.randomUUID());
        ticket.registrarSaida(Instant.now());
        UUID ticketId = UUID.randomUUID();
        ReflectionTestUtils.setField(ticket, "id", ticketId);
        when(localizador.buscarParaAlteracao(ticketId)).thenReturn(ticket);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UUID pagamentoId = UUID.randomUUID();
        TicketResponse calculado = service.registrarPagamentoCalculado(ticketId, pagamentoId, new BigDecimal("20.00"));
        TicketResponse finalizado = service.confirmarPagamento(ticketId, pagamentoId, new BigDecimal("20.00"));

        assertEquals(TicketStatus.AGUARDANDO_PAGAMENTO, calculado.status());
        assertEquals(new BigDecimal("20.00"), calculado.valor());
        assertEquals(TicketStatus.FINALIZADO, finalizado.status());
        verify(registroEvento).registrarLiberacao(ticketId, ticket.getVagaId());
    }

    private <T> MensagemEnvelope<T> envelope(UUID messageId, UUID ticketId, TipoMensagem tipo, T payload) {
        return new MensagemEnvelope<>(messageId, ticketId, tipo, Instant.now(), payload);
    }
}
