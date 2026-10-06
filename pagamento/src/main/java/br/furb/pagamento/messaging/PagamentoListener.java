package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.service.PagamentoService;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PagamentoListener {
    private final PagamentoService pagamentoService;

    public PagamentoListener(PagamentoService pagamentoService) {
        this.pagamentoService = pagamentoService;
    }

    @RabbitListener(queues = RabbitMQConfig.PAGAMENTO_QUEUE)
    public void receberCalculo(MensagemEnvelope<CalcularPagamentoRequest> envelope) {
        pagamentoService.processarCalculo(envelope);
    }
}
