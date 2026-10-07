package br.furb.pagamento.service;

import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoConfirmadoEvent;
import br.furb.pagamento.enums.PagamentoStatus;
import br.furb.pagamento.repository.EventoPendenteRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RegistroEventoServiceTest {
    private final EventoPendenteRepository eventos = mock(EventoPendenteRepository.class);
    private final JsonMapper conversor = JsonMapper.builder().findAndAddModules().build();
    private final RegistroEventoService registro = new RegistroEventoService(eventos, conversor);

    @Test
    void devePreservarEnvelopeEPayloadDoPagamentoCalculado() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();

        registro.registrarPagamentoCalculado(new PagamentoCalculadoEvent(
                pagamentoId, ticketId, new BigDecimal("20.00"), Instant.parse("2026-10-03T12:00:00Z"), PagamentoStatus.CALCULADO));

        var envelope = envelopeRegistrado("pagamento.calculado");

        assertThat(envelope.get("messageId").asString()).isNotBlank();
        assertThat(envelope.get("correlationId").asString()).isEqualTo(ticketId.toString());
        assertThat(envelope.get("tipo").asString()).isEqualTo("PAGAMENTO_CALCULADO");
        assertThat(envelope.get("timestamp").asString()).endsWith("Z");
        assertThat(envelope.get("payload").get("pagamentoId").asString()).isEqualTo(pagamentoId.toString());
        assertThat(envelope.get("payload").get("ticketId").asString()).isEqualTo(ticketId.toString());
        assertThat(envelope.get("payload").get("valor").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(envelope.get("payload").get("status").asString()).isEqualTo("CALCULADO");
    }

    @Test
    void devePreservarEnvelopeEPayloadDoPagamentoConfirmado() {
        UUID ticketId = UUID.randomUUID();

        registro.registrarPagamentoConfirmado(new PagamentoConfirmadoEvent(
                UUID.randomUUID(), ticketId, new BigDecimal("20.00"), "PIX", PagamentoStatus.PAGO));

        var envelope = envelopeRegistrado("pagamento.confirmado");

        assertThat(envelope.get("correlationId").asString()).isEqualTo(ticketId.toString());
        assertThat(envelope.get("tipo").asString()).isEqualTo("PAGAMENTO_CONFIRMADO");
        assertThat(envelope.get("payload").get("metodo").asString()).isEqualTo("PIX");
        assertThat(envelope.get("payload").get("status").asString()).isEqualTo("PAGO");
    }

    private tools.jackson.databind.JsonNode envelopeRegistrado(String rota) {
        var capturado = ArgumentCaptor.forClass(String.class);
        verify(eventos).registrar(any(UUID.class), eq(rota), capturado.capture(), any(Instant.class));

        return conversor.readTree(capturado.getValue());
    }
}
