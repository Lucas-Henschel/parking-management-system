package br.furb.vagas.messaging;

import br.furb.vagas.entity.EventoPendente;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.ReturnedMessage;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class VagaPublisherTest {
    private EventoPendente evento() {
        return new EventoPendente(UUID.randomUUID(), "vaga.reservada", "{}", Instant.now());
    }

    private RabbitTemplate configurarBroker(boolean confirmou, boolean devolveu) {
        RabbitTemplate rabbit = mock(RabbitTemplate.class);
        doAnswer(chamada -> {
            Message mensagem = chamada.getArgument(2);
            assertThat(mensagem.getMessageProperties().getDeliveryMode()).isEqualTo(MessageDeliveryMode.PERSISTENT);
            assertThat(mensagem.getMessageProperties().getContentType()).isEqualTo("application/json");
            CorrelationData confirmacao = chamada.getArgument(3);
            if (devolveu) confirmacao.setReturned(new ReturnedMessage(mensagem, 312, "NO_ROUTE",
                    "parking.exchange", "vaga.reservada"));
            confirmacao.getFuture().complete(new CorrelationData.Confirm(confirmou, null));
            return null;
        }).when(rabbit).send(eq("parking.exchange"), eq("vaga.reservada"), any(Message.class), any(CorrelationData.class));
        return rabbit;
    }

    @Test
    void deveAceitarConfirmacaoDoBroker() {
        var rabbit = configurarBroker(true, false);
        assertThatCode(() -> new VagaPublisher(rabbit).publicar(evento())).doesNotThrowAnyException();
    }

    @Test
    void deveRejeitarNackOuMensagemSemRota() {
        var evento = evento();
        assertThatThrownBy(() -> new VagaPublisher(configurarBroker(false, false)).publicar(evento))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new VagaPublisher(configurarBroker(true, true)).publicar(evento))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void deveUsarOIdDoEventoComoMessageId() {
        var evento = evento();
        var rabbit = mock(RabbitTemplate.class);
        doAnswer(chamada -> {
            Message mensagem = chamada.getArgument(2);
            assertThat(mensagem.getMessageProperties().getMessageId()).isEqualTo(evento.obterId().toString());
            CorrelationData confirmacao = chamada.getArgument(3);
            confirmacao.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(any(String.class), any(String.class), any(Message.class), any(CorrelationData.class));

        assertThatCode(() -> new VagaPublisher(rabbit).publicar(evento)).doesNotThrowAnyException();
    }
}
