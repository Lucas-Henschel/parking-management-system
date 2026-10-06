package br.furb.estacionamento.messaging;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.PagamentoCalculadoPayload;
import br.furb.estacionamento.dto.PagamentoConfirmadoPayload;
import br.furb.estacionamento.dto.VagaResultadoPayload;
import br.furb.estacionamento.service.PagamentoTicketService;
import br.furb.estacionamento.service.ResultadoVagaService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class TicketListener {
    private final ResultadoVagaService resultadoVagaService;
    private final PagamentoTicketService pagamentoTicketService;

    public TicketListener(ResultadoVagaService resultadoVagaService, PagamentoTicketService pagamentoTicketService) {
        this.resultadoVagaService = resultadoVagaService;
        this.pagamentoTicketService = pagamentoTicketService;
    }

    @RabbitListener(queues = RabbitMQConfig.VAGA_RESULTADO_QUEUE)
    public void receberResultadoVaga(MensagemEnvelope<VagaResultadoPayload> envelope) {
        resultadoVagaService.processarResultadoVaga(envelope);
    }

    @RabbitListener(queues = RabbitMQConfig.PAGAMENTO_CALCULADO_QUEUE)
    public void receberPagamentoCalculado(MensagemEnvelope<PagamentoCalculadoPayload> envelope) {
        pagamentoTicketService.processarPagamentoCalculado(envelope);
    }

    @RabbitListener(queues = RabbitMQConfig.PAGAMENTO_CONFIRMADO_QUEUE)
    public void receberPagamentoConfirmado(MensagemEnvelope<PagamentoConfirmadoPayload> envelope) {
        pagamentoTicketService.processarPagamentoConfirmado(envelope);
    }
}
