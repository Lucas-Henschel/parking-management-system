package br.furb.vagas.service;

import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.ReservarVagaRequest;
import br.furb.vagas.enums.TipoMensagem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MensagemValidadorTest {
    private final MensagemValidador validador = new MensagemValidador();
    private final UUID ticketId = UUID.randomUUID();

    private MensagemEnvelope<ReservarVagaRequest> envelope(TipoMensagem tipo, UUID correlationId) {
        return new MensagemEnvelope<>(UUID.randomUUID(), correlationId, tipo, Instant.now(),
                new ReservarVagaRequest(ticketId));
    }

    @Test
    void deveAceitarEnvelopeValido() {
        var envelope = envelope(TipoMensagem.RESERVAR_VAGA, ticketId);

        assertThatCode(() -> {
            validador.validarEnvelope(envelope, TipoMensagem.RESERVAR_VAGA);
            validador.validarTicket(envelope, ticketId);
        }).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarTipoDiferenteDoEsperado() {
        var envelope = envelope(TipoMensagem.LIBERAR_VAGA, ticketId);

        assertThatThrownBy(() -> validador.validarEnvelope(envelope, TipoMensagem.RESERVAR_VAGA))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarEnvelopeNuloOuIncompleto() {
        var semMessageId = new MensagemEnvelope<>(null, ticketId, TipoMensagem.RESERVAR_VAGA, Instant.now(),
                new ReservarVagaRequest(ticketId));
        var semCorrelationId = new MensagemEnvelope<>(UUID.randomUUID(), null, TipoMensagem.RESERVAR_VAGA,
                Instant.now(), new ReservarVagaRequest(ticketId));
        var semTimestamp = new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.RESERVAR_VAGA, null,
                new ReservarVagaRequest(ticketId));
        var semPayload = new MensagemEnvelope<ReservarVagaRequest>(UUID.randomUUID(), ticketId,
                TipoMensagem.RESERVAR_VAGA, Instant.now(), null);

        assertThatThrownBy(() -> validador.validarEnvelope(null, TipoMensagem.RESERVAR_VAGA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validador.validarEnvelope(semMessageId, TipoMensagem.RESERVAR_VAGA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validador.validarEnvelope(semCorrelationId, TipoMensagem.RESERVAR_VAGA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validador.validarEnvelope(semTimestamp, TipoMensagem.RESERVAR_VAGA))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validador.validarEnvelope(semPayload, TipoMensagem.RESERVAR_VAGA))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deveRejeitarTicketAusente() {
        var envelope = envelope(TipoMensagem.RESERVAR_VAGA, ticketId);

        assertThatThrownBy(() -> validador.validarTicket(envelope, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("ticketId");
    }

    @Test
    void deveRejeitarCorrelationIdDiferenteDoTicketId() {
        var envelope = envelope(TipoMensagem.RESERVAR_VAGA, UUID.randomUUID());

        assertThatThrownBy(() -> validador.validarTicket(envelope, ticketId))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("correlationId");
    }
}
