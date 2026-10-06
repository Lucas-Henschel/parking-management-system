package br.furb.estacionamento.service;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.dto.CalcularPagamentoPayload;
import br.furb.estacionamento.repository.EventoPendenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RegistroEventoServiceTest {

    @Mock
    private EventoPendenteRepository eventos;

    private final JsonMapper mapper = JsonMapper.builder().build();

    private RegistroEventoService registroEvento;

    @BeforeEach
    void setUp() {
        registroEvento = new RegistroEventoService(eventos, mapper);
    }

    @Test
    void registraReservaComEnvelopeCorrelacionadoAoTicket() {
        UUID ticketId = UUID.randomUUID();

        registroEvento.registrarReserva(ticketId);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> id = ArgumentCaptor.forClass(UUID.class);
        verify(eventos).registrar(id.capture(), eq(RabbitMQConfig.VAGA_RESERVAR_ROUTING_KEY), captor.capture(),
                org.mockito.ArgumentMatchers.any());
        var envelope = mapper.readTree(captor.getValue());
        assertEquals(id.getValue().toString(), envelope.get("messageId").asString());
        assertEquals(ticketId.toString(), envelope.get("correlationId").asString());
        assertEquals("RESERVAR_VAGA", envelope.get("tipo").asString());
        assertEquals(ticketId.toString(), envelope.get("payload").get("ticketId").asString());
    }

    @Test
    void registraCalculoComDatasInstant() {
        UUID ticketId = UUID.randomUUID();
        CalcularPagamentoPayload payload = new CalcularPagamentoPayload(
                ticketId, Instant.parse("2026-10-04T12:00:00Z"), Instant.parse("2026-10-04T14:00:00Z"));

        registroEvento.registrarCalculoPagamento(payload);

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(eventos).registrar(org.mockito.ArgumentMatchers.any(),
                eq(RabbitMQConfig.PAGAMENTO_CALCULAR_ROUTING_KEY), captor.capture(), org.mockito.ArgumentMatchers.any());
        var envelope = mapper.readTree(captor.getValue());
        assertEquals(ticketId.toString(), envelope.get("correlationId").asString());
        assertEquals("CALCULAR_PAGAMENTO", envelope.get("tipo").asString());
        assertEquals("2026-10-04T12:00:00Z", envelope.get("payload").get("entrada").asString());
    }
}
