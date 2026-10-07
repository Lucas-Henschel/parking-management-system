package br.furb.pagamento.service;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoConfirmadoEvent;
import br.furb.pagamento.repository.EventoPendenteRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Grava os eventos de saída na outbox. Deve ser chamado dentro da transação que altera o
 * pagamento, para que o estado e o evento sejam confirmados (ou desfeitos) juntos.
 */
@Service
public class RegistroEventoService {
    private final EventoPendenteRepository eventoPendenteRepository;
    private final ObjectMapper conversor;

    public RegistroEventoService(EventoPendenteRepository eventoPendenteRepository, ObjectMapper conversor) {
        this.eventoPendenteRepository = eventoPendenteRepository;
        this.conversor = conversor;
    }

    public void registrarPagamentoCalculado(PagamentoCalculadoEvent evento) {
        registrar(evento.ticketId(), "PAGAMENTO_CALCULADO", RabbitMQConfig.PAGAMENTO_CALCULADO_ROUTING_KEY, evento);
    }

    public void registrarPagamentoConfirmado(PagamentoConfirmadoEvent evento) {
        registrar(evento.ticketId(), "PAGAMENTO_CONFIRMADO", RabbitMQConfig.PAGAMENTO_CONFIRMADO_ROUTING_KEY, evento);
    }

    private <T> void registrar(UUID ticketId, String tipo, String rota, T payload) {
        UUID messageId = UUID.randomUUID();
        Instant agora = Instant.now();

        MensagemEnvelope<T> envelope = new MensagemEnvelope<>(messageId, ticketId, tipo, agora, payload);

        eventoPendenteRepository.registrar(messageId, rota, conversor.writeValueAsString(envelope), agora);
    }
}
