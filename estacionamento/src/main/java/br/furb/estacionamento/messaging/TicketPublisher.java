package br.furb.estacionamento.messaging;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.dto.CalcularPagamentoPayload;
import br.furb.estacionamento.dto.LiberarVagaPayload;
import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.ReservarVagaPayload;
import br.furb.estacionamento.repository.EventoPendenteRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Component
@Transactional(propagation = Propagation.MANDATORY)
public class TicketPublisher {

    private final EventoPendenteRepository eventos;
    private final ObjectMapper mapper;

    public TicketPublisher(EventoPendenteRepository eventos, ObjectMapper mapper) {
        this.eventos = eventos;
        this.mapper = mapper;
    }

    public void publicarReserva(UUID ticketId) {
        publicar(RabbitMQConfig.VAGA_RESERVAR_ROUTING_KEY, "RESERVAR_VAGA", ticketId,
                new ReservarVagaPayload(ticketId));
    }

    public void publicarLiberacao(UUID ticketId, UUID vagaId) {
        publicar(RabbitMQConfig.VAGA_LIBERAR_ROUTING_KEY, "LIBERAR_VAGA", ticketId,
                new LiberarVagaPayload(ticketId, vagaId));
    }

    public void publicarCalculoPagamento(CalcularPagamentoPayload payload) {
        publicar(RabbitMQConfig.PAGAMENTO_CALCULAR_ROUTING_KEY, "CALCULAR_PAGAMENTO", payload.ticketId(), payload);
    }

    private void publicar(String routingKey, String tipo, UUID ticketId, Object payload) {
        MensagemEnvelope<Object> envelope = new MensagemEnvelope<>(
                UUID.randomUUID(), ticketId, tipo, Instant.now(), payload);
        eventos.registrar(envelope.messageId(), routingKey, mapper.writeValueAsString(envelope), envelope.timestamp());
    }
}
