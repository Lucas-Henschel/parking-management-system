package br.furb.vagas.service;

import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.ReservarVagaRequest;
import br.furb.vagas.entity.ReservaTicket;
import br.furb.vagas.entity.Vaga;
import br.furb.vagas.enums.MotivoIndisponibilidade;
import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.repository.MensagemProcessadaRepository;
import br.furb.vagas.repository.VagaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class ReservaVagaService {
    private static final Logger log = LoggerFactory.getLogger(ReservaVagaService.class);
    private final MensagemValidador validador;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;
    private final ReservaTicketService reservaTicketService;
    private final VagaRepository vagaRepository;
    private final RegistroResultadoReservaService resultados;

    public ReservaVagaService(
        MensagemValidador validador,
        MensagemProcessadaRepository mensagemProcessadaRepository,
        ReservaTicketService reservaTicketService,
        VagaRepository vagaRepository,
        RegistroResultadoReservaService resultados
    ) {
        this.validador = validador;
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
        this.reservaTicketService = reservaTicketService;
        this.vagaRepository = vagaRepository;
        this.resultados = resultados;
    }

    /**
     * Processa RESERVAR_VAGA de forma idempotente: o messageId é registrado na mesma transação da
     * reserva; se já existia, a mensagem é duplicada e é ignorada.
     */
    @Transactional
    public void reservar(MensagemEnvelope<ReservarVagaRequest> envelope) {
        validador.validarEnvelope(envelope, TipoMensagem.RESERVAR_VAGA);

        UUID ticketId = envelope.payload().ticketId();
        validador.validarTicket(envelope, ticketId);

        if (mensagemProcessadaRepository.registrarSeAusente(envelope.messageId(), Instant.now()) == 0) {
            return;
        }

        ReservaTicket reserva = reservaTicketService.travar(ticketId);

        switch (reserva.obterSituacao()) {
            case RESERVADA -> resultados.registrarReserva(ticketId, reserva.obterVagaId(), reserva.obterNumeroVaga());
            case LIBERADA -> resultados.registrarIndisponibilidade(ticketId, MotivoIndisponibilidade.TICKET_FINALIZADO);
            case PENDENTE, INDISPONIVEL -> reservarVagaLivre(reserva, envelope.messageId());
        }
    }

    private void reservarVagaLivre(ReservaTicket reserva, UUID messageId) {
        UUID ticketId = reserva.obterTicketId();
        log.info("Buscando vaga livre: ticketId={}, messageId={}", ticketId, messageId);
        Optional<Vaga> vagaLivre = vagaRepository.buscarLivreParaReserva();

        if (vagaLivre.isEmpty()) {
            log.info("Busca sem vagas: ticketId={}, messageId={}, motivo=SEM_VAGAS", ticketId, messageId);
            reserva.registrarIndisponibilidade();
            resultados.registrarIndisponibilidade(ticketId, MotivoIndisponibilidade.SEM_VAGAS);
            return;
        }

        Vaga vaga = vagaLivre.get();
        vaga.reservar(ticketId);
        reserva.registrarReserva(vaga.obterId(), vaga.obterNumero());
        resultados.registrarReserva(ticketId, vaga.obterId(), vaga.obterNumero());
    }
}
