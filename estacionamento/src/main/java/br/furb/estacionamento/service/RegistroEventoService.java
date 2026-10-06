package br.furb.estacionamento.service;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.dto.CalcularPagamentoPayload;
import br.furb.estacionamento.dto.LiberarVagaPayload;
import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.ReservarVagaPayload;
import br.furb.estacionamento.enums.TipoMensagem;
import br.furb.estacionamento.repository.EventoPendenteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

/**
 * Grava na outbox, na mesma transação da mudança de estado do ticket, os eventos que serão
 * publicados no RabbitMQ pelo OutboxScheduler.
 */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class RegistroEventoService {
    private final EventoPendenteRepository eventoPendenteRepository;
    private final ObjectMapper conversor;

    public RegistroEventoService(EventoPendenteRepository eventoPendenteRepository, ObjectMapper conversor) {
        this.eventoPendenteRepository = eventoPendenteRepository;
        this.conversor = conversor;
    }

    public void registrarReserva(UUID ticketId) {
        registrar(
            ticketId,
            TipoMensagem.RESERVAR_VAGA,
            RabbitMQConfig.VAGA_RESERVAR_ROUTING_KEY,
            new ReservarVagaPayload(ticketId)
        );
    }

    public void registrarLiberacao(UUID ticketId, UUID vagaId) {
        registrar(
            ticketId,
            TipoMensagem.LIBERAR_VAGA,
            RabbitMQConfig.VAGA_LIBERAR_ROUTING_KEY,
            new LiberarVagaPayload(ticketId, vagaId)
        );
    }

    public void registrarCalculoPagamento(CalcularPagamentoPayload payload) {
        registrar(
            payload.ticketId(),
            TipoMensagem.CALCULAR_PAGAMENTO,
            RabbitMQConfig.PAGAMENTO_CALCULAR_ROUTING_KEY,
            payload
        );
    }

    private <T> void registrar(UUID ticketId, TipoMensagem tipo, String rota, T payload) {
        UUID messageId = UUID.randomUUID();
        Instant agora = Instant.now();

        MensagemEnvelope<T> envelope = new MensagemEnvelope<>(messageId, ticketId, tipo, agora, payload);

        eventoPendenteRepository.registrar(messageId, rota, conversor.writeValueAsString(envelope), agora);
    }
}
