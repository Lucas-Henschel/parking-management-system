package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.dto.VagaResultadoPayload;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.enums.TipoMensagem;
import br.furb.estacionamento.repository.MensagemProcessadaRepository;
import br.furb.estacionamento.repository.TicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResultadoVagaServiceTest {

    @Mock
    private MensagemProcessadaRepository mensagemProcessadaRepository;

    @Mock
    private TicketLocalizador localizador;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private RegistroEventoService registroEvento;

    private ResultadoVagaService service;

    @BeforeEach
    void setUp() {
        service = new ResultadoVagaService(
                new MensagemValidador(), mensagemProcessadaRepository, localizador, ticketRepository, registroEvento);
    }

    @Test
    void processaReservaERegistraMessageId() {
        UUID ticketId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID vagaId = UUID.randomUUID();
        Ticket ticket = new Ticket(new Veiculo("ABC1234"), Instant.now());
        MensagemEnvelope<VagaResultadoPayload> envelope = new MensagemEnvelope<>(
                messageId, ticketId, TipoMensagem.VAGA_RESERVADA, Instant.now(),
                new VagaResultadoPayload(ticketId, vagaId, "A-12", null));
        when(mensagemProcessadaRepository.registrarSeAusente(eq(messageId), any(Instant.class))).thenReturn(1);
        when(localizador.buscarParaAlteracao(ticketId)).thenReturn(ticket);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.processarResultadoVaga(envelope);

        assertEquals(TicketStatus.ATIVO, ticket.getStatus());
        assertEquals(vagaId, ticket.getVagaId());
    }

    @Test
    void deveAtivarTicketQuandoVagaForConfirmada() {
        Ticket ticket = new Ticket(new Veiculo("ABC1234"), Instant.now());
        UUID ticketId = UUID.randomUUID();
        UUID vagaId = UUID.randomUUID();
        when(localizador.buscarParaAlteracao(ticketId)).thenReturn(ticket);
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TicketResponse response = service.registrarVagaConfirmada(ticketId, vagaId);

        assertEquals(TicketStatus.ATIVO, response.status());
        assertEquals(vagaId, response.vagaId());
    }
}
