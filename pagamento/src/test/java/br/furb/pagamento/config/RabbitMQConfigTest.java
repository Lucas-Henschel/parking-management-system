package br.furb.pagamento.config;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.MensagemEnvelope;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void conversorSerializaEDesserializaEnvelopeComInstant() throws Exception {
        MessageConverter converter = config.messageConverter();
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        MensagemEnvelope<CalcularPagamentoRequest> original = new MensagemEnvelope<>(
                UUID.randomUUID(), ticketId, "CALCULAR_PAGAMENTO", Instant.parse("2026-10-03T12:00:00Z"),
                new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(2, ChronoUnit.HOURS)));

        Message message = converter.toMessage(original, new MessageProperties());
        Object lido = ((JacksonJsonMessageConverter) converter)
                .fromMessage(message, new ParameterizedTypeReference<MensagemEnvelope<CalcularPagamentoRequest>>() {});

        assertEquals(original, lido);
    }

    @Test
    void errosPermanentesNaoSaoRetentados() {
        RetryPolicy policy = politica();

        assertFalse(policy.shouldRetry(new IllegalArgumentException("saída anterior à entrada")));
        assertFalse(policy.shouldRetry(new MessageConversionException("json inválido")));
        assertFalse(policy.shouldRetry(new ListenerExecutionFailedException(
                "falha", new IllegalArgumentException("dados inválidos"))));
    }

    @Test
    void errosTransitoriosSaoRetentados() {
        RetryPolicy policy = politica();

        assertTrue(policy.shouldRetry(new IllegalStateException("banco indisponível")));
        assertTrue(policy.shouldRetry(new ListenerExecutionFailedException("falha", new RuntimeException("timeout"))));
    }

    private RetryPolicy politica() {
        RetryPolicySettings settings = new RetryPolicySettings();
        config.retrySettingsCustomizer().customize(settings);
        return settings.createRetryPolicy();
    }
}
