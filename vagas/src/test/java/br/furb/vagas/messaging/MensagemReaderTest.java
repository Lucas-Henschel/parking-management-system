package br.furb.vagas.messaging;

import br.furb.vagas.messaging.*;
import br.furb.vagas.dto.MensagemEnvelope;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;

class MensagemReaderTest {
    private final MensagemReader leitor = new MensagemReader(JsonMapper.builder().findAndAddModules().build());
    private byte[] mensagem(UUID ticketId, UUID correlacao, String tipo, String adicional) {
        return ("""
                {"messageId":"%s","correlationId":"%s","tipo":"%s","timestamp":"2026-10-04T12:00:00Z",
                "payload":{"ticketId":"%s"%s}}
                """.formatted(UUID.randomUUID(), correlacao, tipo, ticketId, adicional)).getBytes(StandardCharsets.UTF_8);
    }
    @Test
    void deveLerEnvelopeDoContrato() {
        UUID ticketId = UUID.randomUUID();
        var envelope = leitor.ler(mensagem(ticketId, ticketId, "RESERVAR_VAGA", ""), "RESERVAR_VAGA");
        assertThat(envelope.correlacaoId()).isEqualTo(ticketId);
        assertThat(leitor.lerIdentificador(envelope, "ticketId")).isEqualTo(ticketId);
    }
    @Test
    void deveRejeitarCorrelacaoDivergente() {
        assertThatThrownBy(() -> leitor.ler(mensagem(UUID.randomUUID(), UUID.randomUUID(), "RESERVAR_VAGA", ""),
                "RESERVAR_VAGA")).isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
    @Test
    void deveRejeitarLiberacaoSemVaga() {
        UUID ticketId = UUID.randomUUID();
        assertThatThrownBy(() -> leitor.ler(mensagem(ticketId, ticketId, "LIBERAR_VAGA", ""), "LIBERAR_VAGA"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
    @Test
    void deveRejeitarJsonInvalidoETipoIncorreto() {
        assertThatThrownBy(() -> leitor.ler("{".getBytes(StandardCharsets.UTF_8), "RESERVAR_VAGA"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
        UUID ticketId = UUID.randomUUID();
        assertThatThrownBy(() -> leitor.ler(mensagem(ticketId, ticketId, "LIBERAR_VAGA", ""), "RESERVAR_VAGA"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }
}
