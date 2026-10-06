package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.dto.VagaResultadoPayload;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.enums.MotivoIndisponibilidade;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.enums.TipoMensagem;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import br.furb.estacionamento.repository.MensagemProcessadaRepository;
import br.furb.estacionamento.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ResultadoVagaService {
    private static final int MAXIMO_TENTATIVAS_RESERVA = 3;

    private final MensagemValidador validador;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;
    private final TicketLocalizador localizador;
    private final TicketRepository ticketRepository;
    private final RegistroEventoService registroEvento;

    public ResultadoVagaService(
        MensagemValidador validador,
        MensagemProcessadaRepository mensagemProcessadaRepository,
        TicketLocalizador localizador,
        TicketRepository ticketRepository,
        RegistroEventoService registroEvento
    ) {
        this.validador = validador;
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
        this.localizador = localizador;
        this.ticketRepository = ticketRepository;
        this.registroEvento = registroEvento;
    }

    @Transactional
    public void processarResultadoVaga(MensagemEnvelope<VagaResultadoPayload> envelope) {
        validador.validarEnvelope(envelope, TipoMensagem.VAGA_RESERVADA, TipoMensagem.VAGA_INDISPONIVEL);

        VagaResultadoPayload payload = envelope.payload();

        validador.validarTicket(envelope, payload.ticketId());

        if (mensagemProcessadaRepository.registrarSeAusente(envelope.messageId(), Instant.now()) == 0) {
            return;
        }

        switch (envelope.tipo()) {
            case VAGA_RESERVADA -> registrarVagaConfirmada(payload.ticketId(), payload.vagaId());
            case VAGA_INDISPONIVEL -> registrarVagaIndisponivel(payload.ticketId(), payload.motivo());
            default -> throw new IllegalArgumentException("Tipo de resultado de vaga desconhecido: " + envelope.tipo());
        }
    }

    @Transactional
    public TicketResponse registrarVagaConfirmada(UUID ticketId, UUID vagaId) {
        if (vagaId == null) {
            throw new IllegalArgumentException("vagaId é obrigatório");
        }

        Ticket ticket = localizador.buscarParaAlteracao(ticketId);

        if (ticket.getStatus() != TicketStatus.PENDENTE && Objects.equals(ticket.getVagaId(), vagaId)) {
            return TicketResponse.from(ticket);
        }

        if (ticket.getStatus() != TicketStatus.PENDENTE) {
            throw new EstadoInvalidoException("O ticket não aguarda reserva de vaga");
        }

        ticket.confirmarVaga(vagaId);

        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse registrarVagaIndisponivel(UUID ticketId, MotivoIndisponibilidade motivo) {
        if (motivo == null) {
            throw new IllegalArgumentException("Motivo de indisponibilidade inválido");
        }

        Ticket ticket = localizador.buscarParaAlteracao(ticketId);

        if (ticket.getStatus() == TicketStatus.RECUSADO) {
            return TicketResponse.from(ticket);
        }

        if (ticket.getStatus() != TicketStatus.PENDENTE) {
            throw new EstadoInvalidoException("O ticket não aguarda reserva de vaga");
        }

        if (motivo == MotivoIndisponibilidade.SEM_VAGAS && ticket.getTentativasReserva() < MAXIMO_TENTATIVAS_RESERVA) {
            ticket.registrarNovaTentativaReserva();
            registroEvento.registrarReserva(ticket.getId());
        } else {
            ticket.recusar(Instant.now());
        }

        return TicketResponse.from(ticketRepository.save(ticket));
    }
}
