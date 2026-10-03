package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class PagamentoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PagamentoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarPagamentoCalculado(PagamentoCalculadoEvent evento) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAGAMENTO_CALCULADO_ROUTING_KEY,
                evento
        );
    }
}
