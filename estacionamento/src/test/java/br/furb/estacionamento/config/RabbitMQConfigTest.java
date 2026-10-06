package br.furb.estacionamento.config;

import br.furb.estacionamento.enums.Recurso;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import br.furb.estacionamento.exception.RecursoNaoEncontradoException;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.core.retry.RetryPolicy;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {
    private RetryPolicy politica() {
        var settings = new RetryPolicySettings();
        new RabbitMQConfig().retrySettingsCustomizer().customize(settings);
        return settings.createRetryPolicy();
    }

    @Test
    void naoRetentaConflitosPermanentesMesmoQuandoEncapsuladosPeloListener() {
        var conflito = new EstadoInvalidoException("pagamento divergente");
        assertFalse(politica().shouldRetry(new ListenerExecutionFailedException("falha", conflito)));
        assertFalse(politica().shouldRetry(new IllegalArgumentException("dados inválidos")));
        assertFalse(politica().shouldRetry(new RecursoNaoEncontradoException(Recurso.TICKET)));
        assertFalse(politica().shouldRetry(new MessageConversionException("json inválido")));
        assertFalse(politica().shouldRetry(new IllegalArgumentException("envelope inválido")));
    }

    @Test
    void declaraFilasDeResultadoEPagamentoComDlq() {
        var config = new RabbitMQConfig();

        assertEquals("estacionamento.vaga-resultado.queue", config.vagaResultadoQueue().getName());
        assertEquals(RabbitMQConfig.EXCHANGE, config.vagaResultadoQueue().getArguments().get("x-dead-letter-exchange"));
        assertEquals("estacionamento.vaga-resultado.queue.dlq",
            config.vagaResultadoQueue().getArguments().get("x-dead-letter-routing-key"));
        assertEquals("estacionamento.pagamento-calculado.queue.dlq", config.pagamentoCalculadoDlq().getName());
        assertEquals("estacionamento.pagamento-confirmado.queue.dlq", config.pagamentoConfirmadoDlq().getName());
    }

    @Test
    void retentaFalhasTransitorias() {
        assertTrue(politica().shouldRetry(new IllegalStateException("banco indisponível")));
        assertTrue(politica().shouldRetry(new RuntimeException("timeout")));
    }
}
