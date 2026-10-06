package br.furb.vagas.service;

import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.ReservarVagaRequest;
import br.furb.vagas.entity.ReservaTicket;
import br.furb.vagas.entity.Vaga;
import br.furb.vagas.enums.MotivoIndisponibilidade;
import br.furb.vagas.enums.ReservaSituacao;
import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.repository.MensagemProcessadaRepository;
import br.furb.vagas.repository.VagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReservaVagaServiceTest {
    private final MensagemProcessadaRepository mensagens = mock(MensagemProcessadaRepository.class);
    private final ReservaTicketService reservaTicketService = mock(ReservaTicketService.class);
    private final VagaRepository vagas = mock(VagaRepository.class);
    private final RegistroResultadoReservaService resultados = mock(RegistroResultadoReservaService.class);
    private final ReservaVagaService servico = new ReservaVagaService(
        new MensagemValidador(), mensagens, reservaTicketService, vagas, resultados);

    private final UUID ticketId = UUID.randomUUID();
    private final MensagemEnvelope<ReservarVagaRequest> envelope = new MensagemEnvelope<>(
        UUID.randomUUID(), ticketId, TipoMensagem.RESERVAR_VAGA, Instant.now(), new ReservarVagaRequest(ticketId));
    private ReservaTicket reserva;

    @BeforeEach
    void preparar() {
        reserva = new ReservaTicket(ticketId);
        when(mensagens.registrarSeAusente(eq(envelope.messageId()), any(Instant.class))).thenReturn(1);
        when(reservaTicketService.travar(ticketId)).thenReturn(reserva);
    }

    @Test
    void deveIgnorarMensagemJaProcessada() {
        when(mensagens.registrarSeAusente(eq(envelope.messageId()), any(Instant.class))).thenReturn(0);

        servico.reservar(envelope);

        verifyNoInteractions(vagas, resultados, reservaTicketService);
    }

    @Test
    void deveReservarVagaLivre() {
        Vaga vaga = new Vaga("A-1", UUID.randomUUID(), UUID.randomUUID());
        when(vagas.buscarLivreParaReserva()).thenReturn(Optional.of(vaga));

        servico.reservar(envelope);

        assertThat(vaga.obterTicketId()).isEqualTo(ticketId);
        assertThat(reserva.obterSituacao()).isEqualTo(ReservaSituacao.RESERVADA);
        assertThat(reserva.obterNumeroVaga()).isEqualTo("A-1");
        verify(resultados).registrarReserva(ticketId, vaga.obterId(), "A-1");
    }

    @Test
    void deveRegistrarIndisponibilidadeQuandoNaoHaVagaLivre() {
        when(vagas.buscarLivreParaReserva()).thenReturn(Optional.empty());

        servico.reservar(envelope);

        assertThat(reserva.obterSituacao()).isEqualTo(ReservaSituacao.INDISPONIVEL);
        verify(resultados).registrarIndisponibilidade(ticketId, MotivoIndisponibilidade.SEM_VAGAS);
    }

    @Test
    void deveReutilizarReservaDoMesmoTicket() {
        UUID vagaId = UUID.randomUUID();
        reserva.registrarReserva(vagaId, "A-1");

        servico.reservar(envelope);

        verifyNoInteractions(vagas);
        verify(resultados).registrarReserva(ticketId, vagaId, "A-1");
    }

    @Test
    void deveRepetirIndisponibilidadeDoMesmoTicket() {
        reserva.registrarIndisponibilidade();

        servico.reservar(envelope);

        verifyNoInteractions(vagas);
        verify(resultados).registrarIndisponibilidade(ticketId, MotivoIndisponibilidade.SEM_VAGAS);
    }

    @Test
    void deveImpedirReservaDeTicketFinalizado() {
        reserva.encerrar();

        servico.reservar(envelope);

        verifyNoInteractions(vagas);
        verify(resultados).registrarIndisponibilidade(ticketId, MotivoIndisponibilidade.TICKET_FINALIZADO);
    }

    @Test
    void deveValidarAntesDeRegistrarAMensagem() {
        var divergente = new MensagemEnvelope<>(UUID.randomUUID(), UUID.randomUUID(), TipoMensagem.RESERVAR_VAGA,
            Instant.now(), new ReservarVagaRequest(ticketId));

        assertThatThrownBy(() -> servico.reservar(divergente)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mensagens, vagas, resultados, reservaTicketService);
    }
}
