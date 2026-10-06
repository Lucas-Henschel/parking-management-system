package br.furb.vagas.service;

import br.furb.vagas.enums.MotivoIndisponibilidade;
import br.furb.vagas.repository.EventoPendenteRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistroResultadoReservaServiceTest {
    private final EventoPendenteRepository eventos = mock(EventoPendenteRepository.class);
    private final JsonMapper conversor = JsonMapper.builder().findAndAddModules().build();
    private final RegistroResultadoReservaService resultados =
            new RegistroResultadoReservaService(eventos, conversor);

    @Test
    void devePreservarEnvelopePayloadEDataUtcDaReserva() {
        UUID idTicket = UUID.randomUUID();
        UUID idVaga = UUID.randomUUID();

        resultados.registrarReserva(idTicket, idVaga, "A-12");

        var envelopeCapturado = ArgumentCaptor.forClass(String.class);
        verify(eventos).registrar(any(UUID.class), eq("vaga.reservada"), envelopeCapturado.capture(), any(Instant.class));
        var envelope = conversor.readTree(envelopeCapturado.getValue());

        assertThat(envelope.get("messageId").asString()).isNotBlank();
        assertThat(envelope.get("correlationId").asString()).isEqualTo(idTicket.toString());
        assertThat(envelope.get("tipo").asString()).isEqualTo("VAGA_RESERVADA");
        assertThat(envelope.get("timestamp").isString()).isTrue();
        assertThat(envelope.get("timestamp").asString()).endsWith("Z");
        assertThat(envelope.get("payload").get("ticketId").asString()).isEqualTo(idTicket.toString());
        assertThat(envelope.get("payload").get("vagaId").asString()).isEqualTo(idVaga.toString());
        assertThat(envelope.get("payload").get("numeroVaga").asString()).isEqualTo("A-12");
        assertThat(envelope.has("conteudo")).isFalse();
    }

    @Test
    void devePreservarContratoDaIndisponibilidade() {
        UUID idTicket = UUID.randomUUID();

        resultados.registrarIndisponibilidade(idTicket, MotivoIndisponibilidade.SEM_VAGAS);

        var envelopeCapturado = ArgumentCaptor.forClass(String.class);
        verify(eventos).registrar(any(UUID.class), eq("vaga.indisponivel"), envelopeCapturado.capture(), any(Instant.class));
        var envelope = conversor.readTree(envelopeCapturado.getValue());

        assertThat(envelope.get("correlationId").asString()).isEqualTo(idTicket.toString());
        assertThat(envelope.get("tipo").asString()).isEqualTo("VAGA_INDISPONIVEL");
        assertThat(envelope.get("payload").get("ticketId").asString()).isEqualTo(idTicket.toString());
        assertThat(envelope.get("payload").get("motivo").asString()).isEqualTo("SEM_VAGAS");
    }
}
