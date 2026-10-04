package br.furb.vagas.messaging;

import br.furb.vagas.service.PublicacaoOutboxService;
import br.furb.vagas.messaging.VagaPublisher;
import br.furb.vagas.repository.OutboxRepository;
import br.furb.vagas.repository.OutboxRepository.EventoPendente;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

class VagaPublisherTest {
    @Test
    void deveConfirmarSomenteAposPublicacao() {
        OutboxRepository eventos = mock(OutboxRepository.class);
        VagaPublisher publicador = mock(VagaPublisher.class);
        EventoPendente evento = new EventoPendente(UUID.randomUUID(), "vaga.reservada", "{}");
        when(eventos.buscarParaPublicacao()).thenReturn(Optional.of(evento));
        assertThat(new PublicacaoOutboxService(eventos, publicador).publicarProximo()).isTrue();
        var ordem = inOrder(eventos, publicador);
        ordem.verify(eventos).buscarParaPublicacao();
        ordem.verify(publicador).publicar(evento);
        ordem.verify(eventos).confirmarPublicacao(evento.id());
    }
    @Test
    void deveManterEventoPendenteEmFalhaDePublicacao() {
        OutboxRepository eventos = mock(OutboxRepository.class);
        VagaPublisher publicador = mock(VagaPublisher.class);
        EventoPendente evento = new EventoPendente(UUID.randomUUID(), "vaga.reservada", "{}");
        when(eventos.buscarParaPublicacao()).thenReturn(Optional.of(evento));
        doThrow(new IllegalStateException("Broker indisponível")).when(publicador).publicar(evento);
        assertThatThrownBy(() -> new PublicacaoOutboxService(eventos, publicador).publicarProximo())
                .isInstanceOf(IllegalStateException.class);
        verify(eventos, never()).confirmarPublicacao(any());
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
        assertThatCode(() -> new VagaPublisher(rabbit).publicar(
                new EventoPendente(UUID.randomUUID(), "vaga.reservada", "{}"))).doesNotThrowAnyException();
    }
    @Test
    void deveRejeitarNackOuMensagemSemRota() {
        var evento = new EventoPendente(UUID.randomUUID(), "vaga.reservada", "{}");
        assertThatThrownBy(() -> new VagaPublisher(configurarBroker(false, false)).publicar(evento))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new VagaPublisher(configurarBroker(true, true)).publicar(evento))
                .isInstanceOf(IllegalStateException.class);
    }
}
