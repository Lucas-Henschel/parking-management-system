package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoConfirmadoEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PagamentoPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PagamentoPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publicarPagamentoCalculado(PagamentoCalculadoEvent evento) {
        MensagemEnvelope<PagamentoCalculadoEvent> envelope = new MensagemEnvelope<>(
                UUID.randomUUID(),
                evento.ticketId(),
                "PAGAMENTO_CALCULADO",
                LocalDateTime.now(),
                evento
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAGAMENTO_CALCULADO_ROUTING_KEY,
                envelope
        );
    }

    public void publicarPagamentoConfirmado(PagamentoConfirmadoEvent evento) {
        MensagemEnvelope<PagamentoConfirmadoEvent> envelope = new MensagemEnvelope<>(
                UUID.randomUUID(),
                evento.ticketId(),
                "PAGAMENTO_CONFIRMADO",
                LocalDateTime.now(),
                evento
        );
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAGAMENTO_CONFIRMADO_ROUTING_KEY,
                envelope
        );
    }
}
