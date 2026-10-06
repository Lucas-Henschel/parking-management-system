package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.PagamentoCalculadoPayload;
import br.furb.estacionamento.dto.PagamentoConfirmadoPayload;
import br.furb.estacionamento.dto.VagaResultadoPayload;
import br.furb.estacionamento.repository.MensagemProcessadaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class TicketMessageHandler {

    private final MensagemProcessadaRepository mensagemProcessadaRepository;
    private final TicketService ticketService;

    public TicketMessageHandler(
            MensagemProcessadaRepository mensagemProcessadaRepository,
            TicketService ticketService
    ) {
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
        this.ticketService = ticketService;
    }

    @Transactional
    public void processarResultadoVaga(MensagemEnvelope<VagaResultadoPayload> envelope) {
        VagaResultadoPayload payload = envelopeValido(envelope);
        validarTicketCorrelacionado(envelope, payload.ticketId());
        if (!registrarMensagem(envelope.messageId())) {
            return;
        }

        switch (envelope.tipo()) {
            case "VAGA_RESERVADA" -> ticketService.registrarVagaConfirmada(payload.ticketId(), payload.vagaId());
            case "VAGA_INDISPONIVEL" -> ticketService.registrarVagaIndisponivel(payload.ticketId(), payload.motivo());
            default -> throw new IllegalArgumentException("Tipo de resultado de vaga desconhecido: " + envelope.tipo());
        }
    }

    @Transactional
    public void processarPagamentoCalculado(MensagemEnvelope<PagamentoCalculadoPayload> envelope) {
        PagamentoCalculadoPayload payload = envelopeValido(envelope);
        validarTipo(envelope, "PAGAMENTO_CALCULADO");
        validarTicketCorrelacionado(envelope, payload.ticketId());
        validarIdentificador(payload.pagamentoId(), "pagamentoId");
        if (!"CALCULADO".equals(payload.status())) {
            throw new IllegalArgumentException("Status de pagamento calculado inválido");
        }
        if (!registrarMensagem(envelope.messageId())) {
            return;
        }

        ticketService.registrarPagamentoCalculado(payload.ticketId(), payload.pagamentoId(), payload.valor());
    }

    @Transactional
    public void processarPagamentoConfirmado(MensagemEnvelope<PagamentoConfirmadoPayload> envelope) {
        PagamentoConfirmadoPayload payload = envelopeValido(envelope);
        validarTipo(envelope, "PAGAMENTO_CONFIRMADO");
        validarTicketCorrelacionado(envelope, payload.ticketId());
        validarIdentificador(payload.pagamentoId(), "pagamentoId");
        if (!"PAGO".equals(payload.status())) {
            throw new IllegalArgumentException("Status de confirmação de pagamento inválido");
        }
        if (payload.metodo() == null || payload.metodo().isBlank()) {
            throw new IllegalArgumentException("metodo é obrigatório");
        }
        if (!registrarMensagem(envelope.messageId())) {
            return;
        }

        ticketService.confirmarPagamento(payload.ticketId(), payload.pagamentoId(), payload.valor());
    }

    private boolean registrarMensagem(UUID messageId) {
        return mensagemProcessadaRepository.registrarSeAusente(messageId, Instant.now()) == 1;
    }

    private <T> T envelopeValido(MensagemEnvelope<T> envelope) {
        if (envelope == null || envelope.messageId() == null || envelope.correlationId() == null
                || envelope.tipo() == null || envelope.timestamp() == null || envelope.payload() == null) {
            throw new IllegalArgumentException("Envelope de mensagem incompleto");
        }
        return envelope.payload();
    }

    private void validarTipo(MensagemEnvelope<?> envelope, String tipoEsperado) {
        if (!tipoEsperado.equals(envelope.tipo())) {
            throw new IllegalArgumentException("Tipo de mensagem inválido: esperado " + tipoEsperado);
        }
    }

    private void validarTicketCorrelacionado(MensagemEnvelope<?> envelope, UUID ticketId) {
        validarIdentificador(ticketId, "ticketId");
        if (!ticketId.equals(envelope.correlationId())) {
            throw new IllegalArgumentException("correlationId deve corresponder ao ticketId");
        }
    }

    private void validarIdentificador(UUID id, String campo) {
        if (id == null) {
            throw new IllegalArgumentException(campo + " é obrigatório");
        }
    }
}
