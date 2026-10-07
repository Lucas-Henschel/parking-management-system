package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.entity.EventoPendente;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Component
public class PagamentoPublisher {
    private final RabbitTemplate rabbit;

    public PagamentoPublisher(RabbitTemplate rabbit) {
        this.rabbit = rabbit;
    }

    public void publicar(EventoPendente evento) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propriedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propriedades.setMessageId(evento.obterId().toString());

        CorrelationData confirmacao = new CorrelationData(evento.obterId().toString());

        rabbit.send(
            RabbitMQConfig.EXCHANGE,
            evento.obterRota(),
            new Message(evento.obterEnvelope().getBytes(StandardCharsets.UTF_8), propriedades),
            confirmacao
        );

        try {
            var resultado = confirmacao.getFuture().get(10, TimeUnit.SECONDS);

            if (!resultado.ack() || confirmacao.getReturned() != null) {
                throw new IllegalStateException("O broker não confirmou o roteamento da mensagem " + evento.obterId());
            }
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação interrompida.", erro);
        } catch (ExecutionException | TimeoutException erro) {
            throw new IllegalStateException("Não foi possível confirmar a publicação.", erro);
        }
    }
}
