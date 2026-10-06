package br.furb.vagas.service;

import br.furb.vagas.dto.LiberarVagaRequest;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.entity.ReservaTicket;
import br.furb.vagas.entity.Vaga;
import br.furb.vagas.enums.ReservaSituacao;
import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.repository.MensagemProcessadaRepository;
import br.furb.vagas.repository.VagaRepository;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class LiberacaoVagaService {
    private final MensagemValidador validador;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;
    private final ReservaTicketService reservaTicketService;
    private final VagaRepository vagaRepository;

    public LiberacaoVagaService(
        MensagemValidador validador,
        MensagemProcessadaRepository mensagemProcessadaRepository,
        ReservaTicketService reservaTicketService,
        VagaRepository vagaRepository
    ) {
        this.validador = validador;
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
        this.reservaTicketService = reservaTicketService;
        this.vagaRepository = vagaRepository;
    }

    /**
     * Processa LIBERAR_VAGA de forma idempotente. Não publica resposta: a vaga apenas volta a LIVRE.
     */
    @Transactional
    public void liberar(MensagemEnvelope<LiberarVagaRequest> envelope) {
        validador.validarEnvelope(envelope, TipoMensagem.LIBERAR_VAGA);

        UUID ticketId = envelope.payload().ticketId();
        UUID vagaId = envelope.payload().vagaId();
        validador.validarTicket(envelope, ticketId);

        if (vagaId == null) {
            throw new IllegalArgumentException("vagaId é obrigatório.");
        }

        if (mensagemProcessadaRepository.registrarSeAusente(envelope.messageId(), Instant.now()) == 0) {
            return;
        }

        ReservaTicket reserva = reservaTicketService.travar(ticketId);

        if (reserva.obterSituacao() == ReservaSituacao.LIBERADA) {
            return;
        }

        if (reserva.obterSituacao() != ReservaSituacao.RESERVADA) {
            // Um comando fora de ordem encerra o ticket e impede reserva posterior.
            reserva.encerrar();
            return;
        }

        if (!vagaId.equals(reserva.obterVagaId())) {
            throw new AmqpRejectAndDontRequeueException("A vaga informada não pertence ao ticket.");
        }

        Vaga vaga = vagaRepository.buscarParaAlteracao(vagaId)
            .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Vaga reservada inexistente."));

        if (!ticketId.equals(vaga.obterTicketId())) {
            throw new AmqpRejectAndDontRequeueException("Ocupação divergente do ticket.");
        }

        vaga.liberar(ticketId);
        reserva.encerrar();
    }
}
