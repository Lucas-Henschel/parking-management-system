package br.furb.vagas.service;

import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.VagaIndisponivelEvent;
import br.furb.vagas.dto.VagaReservadaEvent;
import br.furb.vagas.repository.OutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RegistroResultadoReservaService {
    private final OutboxRepository eventos;
    private final ObjectMapper conversor;

    public RegistroResultadoReservaService(OutboxRepository eventos, ObjectMapper conversor) {
        this.eventos = eventos;
        this.conversor = conversor;
    }

    public void registrarReserva(UUID idTicket, UUID idVaga, String numero) {
        registrar(idTicket, "VAGA_RESERVADA", "vaga.reservada",
                new VagaReservadaEvent(idTicket, idVaga, numero));
    }

    public void registrarIndisponibilidade(UUID idTicket, String motivo) {
        registrar(idTicket, "VAGA_INDISPONIVEL", "vaga.indisponivel",
                new VagaIndisponivelEvent(idTicket, motivo));
    }

    private void registrar(UUID idTicket, String tipo, String rota, Object conteudo) {
        UUID idMensagem = UUID.randomUUID();
        MensagemEnvelope envelope = new MensagemEnvelope(
                idMensagem, idTicket, tipo, Instant.now(), conversor.valueToTree(conteudo));
        eventos.registrar(idMensagem, rota, conversor.writeValueAsString(envelope));
    }
}
