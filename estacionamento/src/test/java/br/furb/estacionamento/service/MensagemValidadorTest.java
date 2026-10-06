package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.enums.TipoMensagem;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MensagemValidadorTest {
    private final MensagemValidador validador = new MensagemValidador();

    private MensagemEnvelope<Object> envelope(UUID correlationId, TipoMensagem tipo, Object payload) {
        return new MensagemEnvelope<>(UUID.randomUUID(), correlationId, tipo, Instant.now(), payload);
    }

    @Test
    void aceitaEnvelopeCompletoComTipoEsperado() {
        var envelope = envelope(UUID.randomUUID(), TipoMensagem.VAGA_RESERVADA, new Object());

        assertDoesNotThrow(() -> validador.validarEnvelope(envelope, TipoMensagem.VAGA_RESERVADA, TipoMensagem.VAGA_INDISPONIVEL));
    }

    @Test
    void rejeitaEnvelopeNuloOuIncompleto() {
        assertThrows(IllegalArgumentException.class, () -> validador.validarEnvelope(null, TipoMensagem.VAGA_RESERVADA));
        assertThrows(IllegalArgumentException.class,
            () -> validador.validarEnvelope(envelope(null, TipoMensagem.VAGA_RESERVADA, new Object()), TipoMensagem.VAGA_RESERVADA));
        assertThrows(IllegalArgumentException.class,
            () -> validador.validarEnvelope(envelope(UUID.randomUUID(), TipoMensagem.VAGA_RESERVADA, null), TipoMensagem.VAGA_RESERVADA));
    }

    @Test
    void rejeitaTipoDiferenteDoEsperado() {
        var envelope = envelope(UUID.randomUUID(), TipoMensagem.PAGAMENTO_CALCULADO, new Object());

        assertThrows(IllegalArgumentException.class,
            () -> validador.validarEnvelope(envelope, TipoMensagem.PAGAMENTO_CONFIRMADO));
    }

    @Test
    void exigeCorrelationIdIgualAoTicketId() {
        UUID ticketId = UUID.randomUUID();
        var correto = envelope(ticketId, TipoMensagem.VAGA_RESERVADA, new Object());
        var errado = envelope(UUID.randomUUID(), TipoMensagem.VAGA_RESERVADA, new Object());

        assertDoesNotThrow(() -> validador.validarTicket(correto, ticketId));
        assertThrows(IllegalArgumentException.class, () -> validador.validarTicket(errado, ticketId));
        assertThrows(IllegalArgumentException.class, () -> validador.validarTicket(correto, null));
    }

    @Test
    void identificadorNuloEhRejeitado() {
        assertThrows(IllegalArgumentException.class, () -> validador.validarIdentificador(null, "pagamentoId"));
        assertDoesNotThrow(() -> validador.validarIdentificador(UUID.randomUUID(), "pagamentoId"));
    }
}
