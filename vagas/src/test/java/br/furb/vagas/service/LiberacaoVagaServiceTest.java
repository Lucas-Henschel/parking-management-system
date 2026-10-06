package br.furb.vagas.service;

import br.furb.vagas.dto.LiberarVagaRequest;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.entity.ReservaTicket;
import br.furb.vagas.entity.Vaga;
import br.furb.vagas.enums.ReservaSituacao;
import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.repository.MensagemProcessadaRepository;
import br.furb.vagas.repository.VagaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LiberacaoVagaServiceTest {
    private final MensagemProcessadaRepository mensagens = mock(MensagemProcessadaRepository.class);
    private final ReservaTicketService reservaTicketService = mock(ReservaTicketService.class);
    private final VagaRepository vagas = mock(VagaRepository.class);
    private final LiberacaoVagaService servico = new LiberacaoVagaService(
        new MensagemValidador(), mensagens, reservaTicketService, vagas);

    private final UUID ticketId = UUID.randomUUID();
    private ReservaTicket reserva;

    private MensagemEnvelope<LiberarVagaRequest> liberacao(UUID vagaId) {
        return new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.LIBERAR_VAGA, Instant.now(),
            new LiberarVagaRequest(ticketId, vagaId));
    }

    private MensagemEnvelope<LiberarVagaRequest> liberacaoNova(UUID vagaId) {
        var envelope = liberacao(vagaId);
        when(mensagens.registrarSeAusente(eq(envelope.messageId()), any(Instant.class))).thenReturn(1);
        return envelope;
    }

    private Vaga vagaOcupada() {
        Vaga vaga = new Vaga("A-1", UUID.randomUUID(), UUID.randomUUID());
        ReflectionTestUtils.setField(vaga, "id", UUID.randomUUID());
        vaga.reservar(ticketId);
        return vaga;
    }

    @BeforeEach
    void preparar() {
        reserva = new ReservaTicket(ticketId);
        when(reservaTicketService.travar(ticketId)).thenReturn(reserva);
    }

    @Test
    void deveIgnorarMensagemJaProcessada() {
        var envelope = liberacao(UUID.randomUUID());
        when(mensagens.registrarSeAusente(eq(envelope.messageId()), any(Instant.class))).thenReturn(0);

        servico.liberar(envelope);

        verifyNoInteractions(vagas, reservaTicketService);
    }

    @Test
    void deveLiberarVagaDoTicketReservado() {
        Vaga vaga = vagaOcupada();
        reserva.registrarReserva(vaga.obterId(), "A-1");
        when(vagas.buscarParaAlteracao(vaga.obterId())).thenReturn(Optional.of(vaga));

        servico.liberar(liberacaoNova(vaga.obterId()));

        assertThat(vaga.obterTicketId()).isNull();
        assertThat(reserva.obterSituacao()).isEqualTo(ReservaSituacao.LIBERADA);
    }

    @Test
    void deveIgnorarTicketJaLiberado() {
        reserva.encerrar();

        servico.liberar(liberacaoNova(UUID.randomUUID()));

        verifyNoInteractions(vagas);
        assertThat(reserva.obterSituacao()).isEqualTo(ReservaSituacao.LIBERADA);
    }

    @Test
    void deveEncerrarTicketComLiberacaoForaDeOrdem() {
        servico.liberar(liberacaoNova(UUID.randomUUID()));

        verifyNoInteractions(vagas);
        assertThat(reserva.obterSituacao()).isEqualTo(ReservaSituacao.LIBERADA);
    }

    @Test
    void deveRejeitarLiberacaoDeVagaDeOutroTicket() {
        reserva.registrarReserva(UUID.randomUUID(), "A-1");
        var envelope = liberacaoNova(UUID.randomUUID());

        assertThatThrownBy(() -> servico.liberar(envelope)).isInstanceOf(AmqpRejectAndDontRequeueException.class);

        assertThat(reserva.obterSituacao()).isEqualTo(ReservaSituacao.RESERVADA);
    }

    @Test
    void deveRejeitarVagaInexistente() {
        UUID vagaId = UUID.randomUUID();
        reserva.registrarReserva(vagaId, "A-1");
        when(vagas.buscarParaAlteracao(vagaId)).thenReturn(Optional.empty());
        var envelope = liberacaoNova(vagaId);

        assertThatThrownBy(() -> servico.liberar(envelope)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void deveRejeitarOcupacaoDivergenteDoTicket() {
        Vaga vaga = vagaOcupada();
        ReflectionTestUtils.setField(vaga, "ticketId", UUID.randomUUID());
        reserva.registrarReserva(vaga.obterId(), "A-1");
        when(vagas.buscarParaAlteracao(vaga.obterId())).thenReturn(Optional.of(vaga));
        var envelope = liberacaoNova(vaga.obterId());

        assertThatThrownBy(() -> servico.liberar(envelope)).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void deveRejeitarLiberacaoSemVagaIdAntesDeRegistrarAMensagem() {
        assertThatThrownBy(() -> servico.liberar(liberacao(null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vagaId");

        verifyNoInteractions(mensagens, vagas, reservaTicketService);
    }

    @Test
    void deveRejeitarTipoDiferenteDoEsperado() {
        var errada = new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.RESERVAR_VAGA, Instant.now(),
            new LiberarVagaRequest(ticketId, UUID.randomUUID()));

        assertThatThrownBy(() -> servico.liberar(errada)).isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mensagens, vagas, reservaTicketService);
    }
}
