package br.furb.estacionamento.config;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.support.ListenerExecutionFailedException;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.*;

class RabbitMQConfigTest {
    private RetryPolicy politica() {
        var settings = new RetryPolicySettings();
        new RabbitMQConfig().retrySettingsCustomizer().customize(settings);
        return settings.createRetryPolicy();
    }

    @Test
    void naoRetentaConflitosPermanentesMesmoQuandoEncapsuladosPeloListener() {
        var conflito = new ResponseStatusException(HttpStatus.CONFLICT, "pagamento divergente");
        assertFalse(politica().shouldRetry(new ListenerExecutionFailedException("falha", conflito)));
        assertFalse(politica().shouldRetry(new ResponseStatusException(HttpStatus.BAD_REQUEST)));
        assertFalse(politica().shouldRetry(new MessageConversionException("json inválido")));
        assertFalse(politica().shouldRetry(new IllegalArgumentException("envelope inválido")));
    }

    @Test
    void retentaFalhasTransitorias() {
        assertTrue(politica().shouldRetry(new IllegalStateException("banco indisponível")));
        assertTrue(politica().shouldRetry(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE)));
    }
}
