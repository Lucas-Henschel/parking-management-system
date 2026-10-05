package br.furb.vagas.service;

import br.furb.vagas.config.RabbitMQConfig;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.VagaIndisponivelEvent;
import br.furb.vagas.dto.VagaReservadaEvent;
import br.furb.vagas.enums.MotivoIndisponibilidade;
import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.repository.EventoPendenteRepository;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;

@Service
public class RegistroResultadoReservaService {
    private final EventoPendenteRepository eventoPendenteRepository;
    private final ObjectMapper conversor;

    public RegistroResultadoReservaService(EventoPendenteRepository eventoPendenteRepository, ObjectMapper conversor) {
        this.eventoPendenteRepository = eventoPendenteRepository;
        this.conversor = conversor;
    }

    public void registrarReserva(UUID ticketId, UUID vagaId, String numeroVaga) {
        registrar(
            ticketId,
            TipoMensagem.VAGA_RESERVADA,
            RabbitMQConfig.VAGA_RESERVADA_ROUTING_KEY,
            new VagaReservadaEvent(ticketId, vagaId, numeroVaga)
        );
    }

    public void registrarIndisponibilidade(UUID ticketId, MotivoIndisponibilidade motivo) {
        registrar(
            ticketId,
            TipoMensagem.VAGA_INDISPONIVEL,
            RabbitMQConfig.VAGA_INDISPONIVEL_ROUTING_KEY,
            new VagaIndisponivelEvent(ticketId, motivo)
        );
    }

    private <T> void registrar(UUID ticketId, TipoMensagem tipo, String rota, T payload) {
        UUID messageId = UUID.randomUUID();
        Instant agora = Instant.now();

        MensagemEnvelope<T> envelope = new MensagemEnvelope<>(messageId, ticketId, tipo, agora, payload);

        eventoPendenteRepository.registrar(messageId, rota, conversor.writeValueAsString(envelope), agora);
    }
}
