package br.furb.vagas.config;

import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.ReservarVagaRequest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.retry.RetryPolicy;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {
    private final RabbitMQConfig config = new RabbitMQConfig();
    private final TopicExchange exchange = config.parkingExchange();

    @Test
    void exchangeEDeveSerTopicDuravel() {
        assertEquals("parking.exchange", exchange.getName());
        assertTrue(exchange.isDurable());
    }

    @Test
    void filaDeReservaEDuravelEVaiParaDlqNaMesmaExchange() {
        Queue fila = config.vagaReservarQueue();

        assertEquals("vaga.reservar.queue", fila.getName());
        assertTrue(fila.isDurable());
        assertEquals("parking.exchange", fila.getArguments().get("x-dead-letter-exchange"));
        assertEquals("vaga.reservar.dlq", fila.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("vaga.reservar.queue.dlq", config.vagaReservarDlq().getName());
        assertTrue(config.vagaReservarDlq().isDurable());
    }

    @Test
    void filaDeLiberacaoEDuravelEVaiParaDlqNaMesmaExchange() {
        Queue fila = config.vagaLiberarQueue();

        assertEquals("vaga.liberar.queue", fila.getName());
        assertTrue(fila.isDurable());
        assertEquals("parking.exchange", fila.getArguments().get("x-dead-letter-exchange"));
        assertEquals("vaga.liberar.dlq", fila.getArguments().get("x-dead-letter-routing-key"));
        assertEquals("vaga.liberar.queue.dlq", config.vagaLiberarDlq().getName());
        assertTrue(config.vagaLiberarDlq().isDurable());
    }

    @Test
    void bindingsUsamAsRoutingKeysDoContrato() {
        Binding reservar = config.vagaReservarBinding(config.vagaReservarQueue(), exchange);
        Binding reservarDlq = config.vagaReservarDlqBinding(config.vagaReservarDlq(), exchange);
        Binding liberar = config.vagaLiberarBinding(config.vagaLiberarQueue(), exchange);
        Binding liberarDlq = config.vagaLiberarDlqBinding(config.vagaLiberarDlq(), exchange);

        assertEquals("vaga.reservar", reservar.getRoutingKey());
        assertEquals("vaga.reservar.dlq", reservarDlq.getRoutingKey());
        assertEquals("vaga.liberar", liberar.getRoutingKey());
        assertEquals("vaga.liberar.dlq", liberarDlq.getRoutingKey());
        assertEquals("parking.exchange", reservar.getExchange());
    }

    @Test
    void routingKeysDeSaidaSeguemOContrato() {
        assertEquals("vaga.reservada", RabbitMQConfig.VAGA_RESERVADA_ROUTING_KEY);
        assertEquals("vaga.indisponivel", RabbitMQConfig.VAGA_INDISPONIVEL_ROUTING_KEY);
    }

    @Test
    void conversorSerializaEDesserializaEnvelopeComInstant() throws Exception {
        MessageConverter converter = config.messageConverter();
        UUID ticketId = UUID.randomUUID();
        MensagemEnvelope<ReservarVagaRequest> original = new MensagemEnvelope<>(
                UUID.randomUUID(), ticketId, TipoMensagem.RESERVAR_VAGA, Instant.parse("2026-10-03T12:00:00Z"),
                new ReservarVagaRequest(ticketId));

        Message message = converter.toMessage(original, new MessageProperties());
        Object lido = ((JacksonJsonMessageConverter) converter)
                .fromMessage(message, new ParameterizedTypeReference<MensagemEnvelope<ReservarVagaRequest>>() {});

        assertEquals(original, lido);
        assertTrue(new String(message.getBody()).contains("\"timestamp\":\"2026-10-03T12:00:00Z\""));
    }

    @Test
    void errosPermanentesNaoSaoRetentados() {
        RetryPolicy policy = politica();

        assertFalse(policy.shouldRetry(new IllegalArgumentException("envelope inválido")));
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
