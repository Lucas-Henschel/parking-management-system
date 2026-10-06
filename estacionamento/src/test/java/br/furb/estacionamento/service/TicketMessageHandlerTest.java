package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.PagamentoCalculadoPayload;
import br.furb.estacionamento.dto.PagamentoConfirmadoPayload;
import br.furb.estacionamento.dto.VagaResultadoPayload;
import br.furb.estacionamento.repository.MensagemProcessadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TicketMessageHandlerTest {

    @Mock
    private MensagemProcessadaRepository mensagemProcessadaRepository;

    @Mock
    private TicketService ticketService;

    private TicketMessageHandler handler;

    @BeforeEach
    void setUp() {
        handler = new TicketMessageHandler(mensagemProcessadaRepository, ticketService);
    }

    @Test
    void processaReservaERegistraMessageId() {
        UUID ticketId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID vagaId = UUID.randomUUID();
        MensagemEnvelope<VagaResultadoPayload> envelope = envelope(
                messageId, ticketId, "VAGA_RESERVADA", new VagaResultadoPayload(ticketId, vagaId, "A-12", null));
        when(mensagemProcessadaRepository.registrarSeAusente(eq(messageId), any(Instant.class))).thenReturn(1);

        handler.processarResultadoVaga(envelope);

        verify(ticketService).registrarVagaConfirmada(ticketId, vagaId);
    }

    @Test
    void ignoraMensagemDePagamentoJaProcessada() {
        UUID ticketId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MensagemEnvelope<PagamentoCalculadoPayload> envelope = envelope(
                messageId, ticketId, "PAGAMENTO_CALCULADO",
                new PagamentoCalculadoPayload(UUID.randomUUID(), ticketId, new BigDecimal("20.00"), "CALCULADO"));
        when(mensagemProcessadaRepository.registrarSeAusente(eq(messageId), any(Instant.class))).thenReturn(0);

        handler.processarPagamentoCalculado(envelope);

        verify(ticketService, never()).registrarPagamentoCalculado(any(), any(), any());
    }

    @Test
    void rejeitaEnvelopeComCorrelationIdDiferenteDoTicket() {
        UUID ticketId = UUID.randomUUID();
        MensagemEnvelope<PagamentoConfirmadoPayload> envelope = envelope(
                UUID.randomUUID(), UUID.randomUUID(), "PAGAMENTO_CONFIRMADO",
                new PagamentoConfirmadoPayload(UUID.randomUUID(), ticketId, new BigDecimal("20.00"), "PIX", "PAGO"));

        assertThrows(IllegalArgumentException.class, () -> handler.processarPagamentoConfirmado(envelope));
        verify(mensagemProcessadaRepository, never()).registrarSeAusente(any(), any());
    }

    private <T> MensagemEnvelope<T> envelope(UUID messageId, UUID ticketId, String tipo, T payload) {
        return new MensagemEnvelope<>(messageId, ticketId, tipo, Instant.now(), payload);
    }
}
