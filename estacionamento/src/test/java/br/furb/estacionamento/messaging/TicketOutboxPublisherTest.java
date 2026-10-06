package br.furb.estacionamento.messaging;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.entity.EventoPendente;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TicketOutboxPublisherTest {
    private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
    private final TicketOutboxPublisher publisher = new TicketOutboxPublisher(rabbit);
    private final EventoPendente evento = new EventoPendente(UUID.randomUUID(), "vaga.reservar", "{\"tipo\":\"RESERVAR_VAGA\"}", java.time.Instant.now());

    private void confirmar(boolean ack, boolean devolvida) {
        doAnswer(invocation -> {
            Message mensagem = invocation.getArgument(2);
            CorrelationData correlacao = invocation.getArgument(3);
            assertEquals(evento.obterId().toString(), mensagem.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT, mensagem.getMessageProperties().getDeliveryMode());
            if (devolvida) {
                correlacao.setReturned(new ReturnedMessage(mensagem, 312, "NO_ROUTE", RabbitMQConfig.EXCHANGE, evento.obterRota()));
            }
            correlacao.getFuture().complete(new CorrelationData.Confirm(ack, ack ? null : "nack"));
            return null;
        }).when(rabbit).send(eq(RabbitMQConfig.EXCHANGE), eq(evento.obterRota()), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void aceitaSomenteAckComRota() {
        confirmar(true, false);
        assertDoesNotThrow(() -> publisher.publicar(evento));
    }

    @Test
    void nackMantemEventoParaReenvio() {
        confirmar(false, false);
        assertThrows(IllegalStateException.class, () -> publisher.publicar(evento));
    }

    @Test
    void ackSemFilaDeDestinoNaoContaComoEntrega() {
        confirmar(true, true);
        assertThrows(IllegalStateException.class, () -> publisher.publicar(evento));
    }
}
