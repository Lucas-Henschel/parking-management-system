package br.furb.vagas.service;

import br.furb.vagas.entity.*;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.repository.*;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OcupacaoService {
    private final VagaRepository vagas;
    private final FluxoReservaRepository fluxo;
    private final RegistroResultadoReservaService resultados;
    public OcupacaoService(VagaRepository vagas, FluxoReservaRepository fluxo, RegistroResultadoReservaService resultados) {
        this.vagas = vagas; this.fluxo = fluxo; this.resultados = resultados;
    }
    @Transactional
    public void reservar(MensagemEnvelope mensagem, UUID ticketId) {
        if (!fluxo.registrarMensagem(mensagem.mensagemId())) return;
        var registro = fluxo.bloquearTicket(ticketId);
        switch (registro.situacao()) {
            case "RESERVADA" -> resultados.registrarReserva(ticketId, registro.vagaId(), registro.numeroVaga());
            case "INDISPONIVEL" -> resultados.registrarIndisponibilidade(ticketId, "SEM_VAGAS");
            case "LIBERADA" -> resultados.registrarIndisponibilidade(ticketId, "TICKET_FINALIZADO");
            case "PENDENTE" -> reservarLivre(ticketId);
            default -> throw new IllegalStateException("Situação da reserva inválida.");
        }
    }
    private void reservarLivre(UUID ticketId) {
        var vagaLivre = vagas.buscarLivreParaReserva();
        if (vagaLivre.isEmpty()) {
            fluxo.registrarResultado(ticketId, "INDISPONIVEL", null, null);
            resultados.registrarIndisponibilidade(ticketId, "SEM_VAGAS");
            return;
        }
        Vaga vaga = vagaLivre.get(); vaga.reservar(ticketId);
        fluxo.registrarResultado(ticketId, "RESERVADA", vaga.obterId(), vaga.obterNumero());
        resultados.registrarReserva(ticketId, vaga.obterId(), vaga.obterNumero());
    }
    @Transactional
    public void liberar(MensagemEnvelope mensagem, UUID ticketId, UUID vagaId) {
        if (!fluxo.registrarMensagem(mensagem.mensagemId())) return;
        var registro = fluxo.bloquearTicket(ticketId);
        if ("LIBERADA".equals(registro.situacao())) return;
        if ("PENDENTE".equals(registro.situacao()) || "INDISPONIVEL".equals(registro.situacao())) {
            // Um comando fora de ordem encerra o ticket e impede reserva posterior.
            fluxo.registrarResultado(ticketId, "LIBERADA", null, null);
            return;
        }
        if (!vagaId.equals(registro.vagaId()))
            throw new AmqpRejectAndDontRequeueException("A vaga informada não pertence ao ticket.");
        Vaga vaga = vagas.buscarParaAlteracao(vagaId)
                .orElseThrow(() -> new AmqpRejectAndDontRequeueException("Vaga reservada inexistente."));
        if (!ticketId.equals(vaga.obterTicketId()))
            throw new AmqpRejectAndDontRequeueException("Ocupação divergente do ticket.");
        vaga.liberar(ticketId);
        fluxo.registrarResultado(ticketId, "LIBERADA", vagaId, registro.numeroVaga());
    }
}
