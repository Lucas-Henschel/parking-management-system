package br.furb.vagas.service;

import br.furb.vagas.service.*;
import br.furb.vagas.entity.*;
import br.furb.vagas.messaging.*;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.repository.*;
import br.furb.vagas.repository.FluxoReservaRepository.RegistroReserva;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class OcupacaoServiceTest {
    private final VagaRepository vagas = mock(VagaRepository.class);
    private final FluxoReservaRepository fluxo = mock(FluxoReservaRepository.class);
    private final RegistroResultadoReservaService resultados = mock(RegistroResultadoReservaService.class);
    private final OcupacaoService servico = new OcupacaoService(vagas, fluxo, resultados);
    private final UUID ticketId = UUID.randomUUID();
    private final MensagemEnvelope mensagem = new MensagemEnvelope(UUID.randomUUID(), ticketId,
            "RESERVAR_VAGA", Instant.now(), null);
    @BeforeEach
    void preparar() {
        when(fluxo.registrarMensagem(mensagem.mensagemId())).thenReturn(true);
        when(fluxo.bloquearTicket(ticketId)).thenReturn(new RegistroReserva("PENDENTE", null, null));
    }
    @Test
    void deveIgnorarMensagemJaProcessada() {
        when(fluxo.registrarMensagem(mensagem.mensagemId())).thenReturn(false);
        servico.reservar(mensagem, ticketId);
        verifyNoInteractions(vagas, resultados); verify(fluxo, never()).bloquearTicket(any());
    }
    @Test
    void deveRegistrarIndisponibilidade() {
        when(vagas.buscarLivreParaReserva()).thenReturn(Optional.empty());
        servico.reservar(mensagem, ticketId);
        verify(resultados).registrarIndisponibilidade(ticketId, "SEM_VAGAS");
        verify(fluxo).registrarResultado(ticketId, "INDISPONIVEL", null, null);
    }
    @Test
    void deveReservarVagaLivre() {
        Vaga vaga = new Vaga("A-1", UUID.randomUUID(), UUID.randomUUID());
        when(vagas.buscarLivreParaReserva()).thenReturn(Optional.of(vaga));
        servico.reservar(mensagem, ticketId);
        assertThat(vaga.obterTicketId()).isEqualTo(ticketId);
        verify(resultados).registrarReserva(ticketId, vaga.obterId(), "A-1");
    }
    @Test
    void deveReutilizarReservaDoMesmoTicket() {
        UUID vagaId = UUID.randomUUID();
        when(fluxo.bloquearTicket(ticketId)).thenReturn(new RegistroReserva("RESERVADA", vagaId, "A-1"));
        servico.reservar(mensagem, ticketId);
        verifyNoInteractions(vagas); verify(resultados).registrarReserva(ticketId, vagaId, "A-1");
    }
    @Test
    void deveImpedirReservaDeTicketFinalizado() {
        when(fluxo.bloquearTicket(ticketId)).thenReturn(new RegistroReserva("LIBERADA", null, null));
        servico.reservar(mensagem, ticketId);
        verifyNoInteractions(vagas); verify(resultados).registrarIndisponibilidade(ticketId, "TICKET_FINALIZADO");
    }
    @Test
    void deveEncerrarTicketComLiberacaoForaDeOrdem() {
        servico.liberar(mensagem, ticketId, UUID.randomUUID());
        verify(fluxo).registrarResultado(ticketId, "LIBERADA", null, null);
        verifyNoInteractions(vagas, resultados);
    }
}
