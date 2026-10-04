package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.entity.MensagemProcessada;
import br.furb.pagamento.repository.MensagemProcessadaRepository;
import br.furb.pagamento.service.PagamentoService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Component
public class PagamentoListener {

    private final PagamentoService pagamentoService;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;

    public PagamentoListener(PagamentoService pagamentoService, MensagemProcessadaRepository mensagemProcessadaRepository) {
        this.pagamentoService = pagamentoService;
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.PAGAMENTO_QUEUE)
    @Transactional
    public void receberCalculo(MensagemEnvelope<CalcularPagamentoRequest> envelope) {
        if (mensagemProcessadaRepository.existsById(envelope.messageId())) {
            return; 
        }

        pagamentoService.calcularPagamento(envelope.payload());
        
        mensagemProcessadaRepository.save(new MensagemProcessada(envelope.messageId(), LocalDateTime.now()));
    }
}
