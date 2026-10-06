package br.furb.estacionamento.messaging;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.PagamentoCalculadoPayload;
import br.furb.estacionamento.dto.PagamentoConfirmadoPayload;
import br.furb.estacionamento.dto.VagaResultadoPayload;
import br.furb.estacionamento.service.TicketMessageHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TicketListener {

    private final TicketMessageHandler messageHandler;

    public TicketListener(TicketMessageHandler messageHandler) {
        this.messageHandler = messageHandler;
    }

    @RabbitListener(queues = RabbitMQConfig.VAGA_RESULTADO_QUEUE)
    public void receberResultadoVaga(MensagemEnvelope<VagaResultadoPayload> envelope) {
        messageHandler.processarResultadoVaga(envelope);
    }

    @RabbitListener(queues = RabbitMQConfig.PAGAMENTO_CALCULADO_QUEUE)
    public void receberPagamentoCalculado(MensagemEnvelope<PagamentoCalculadoPayload> envelope) {
        messageHandler.processarPagamentoCalculado(envelope);
    }

    @RabbitListener(queues = RabbitMQConfig.PAGAMENTO_CONFIRMADO_QUEUE)
    public void receberPagamentoConfirmado(MensagemEnvelope<PagamentoConfirmadoPayload> envelope) {
        messageHandler.processarPagamentoConfirmado(envelope);
    }
}