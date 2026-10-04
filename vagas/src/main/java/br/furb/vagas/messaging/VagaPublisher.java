package br.furb.vagas.messaging;

import br.furb.vagas.repository.OutboxRepository.EventoPendente;
import br.furb.vagas.config.RabbitMQConfig;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class VagaPublisher {
    private final RabbitTemplate rabbit;
    public VagaPublisher(RabbitTemplate rabbit) { this.rabbit = rabbit; }
    public void publicar(EventoPendente evento) {
        MessageProperties propriedades = new MessageProperties();
        propriedades.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        propriedades.setContentEncoding(StandardCharsets.UTF_8.name());
        propriedades.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        propriedades.setMessageId(evento.id().toString());
        CorrelationData confirmacao = new CorrelationData(evento.id().toString());
        rabbit.send(RabbitMQConfig.EXCHANGE, evento.rota(),
                new Message(evento.envelope().getBytes(StandardCharsets.UTF_8), propriedades), confirmacao);
        try {
            var resultado = confirmacao.getFuture().get(10, TimeUnit.SECONDS);
            if (!resultado.ack() || confirmacao.getReturned() != null)
                throw new IllegalStateException("O broker não confirmou o roteamento da mensagem " + evento.id());
        } catch (InterruptedException erro) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicação interrompida.", erro);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException erro) {
            throw new IllegalStateException("Não foi possível confirmar a publicação.", erro);
        }
    }
}
