package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.dto.PagamentoCalculadoPayload;
import br.furb.estacionamento.dto.PagamentoConfirmadoPayload;
import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.enums.PagamentoStatus;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.enums.TipoMensagem;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import br.furb.estacionamento.repository.MensagemProcessadaRepository;
import br.furb.estacionamento.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PagamentoTicketService {
    private final MensagemValidador validador;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;
    private final TicketLocalizador localizador;
    private final TicketRepository ticketRepository;
    private final RegistroEventoService registroEvento;

    public PagamentoTicketService(
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
    public void processarPagamentoCalculado(MensagemEnvelope<PagamentoCalculadoPayload> envelope) {
        validador.validarEnvelope(envelope, TipoMensagem.PAGAMENTO_CALCULADO);

        PagamentoCalculadoPayload payload = envelope.payload();

        validador.validarTicket(envelope, payload.ticketId());
        validador.validarIdentificador(payload.pagamentoId(), "pagamentoId");

        if (payload.status() != PagamentoStatus.CALCULADO) {
            throw new IllegalArgumentException("Status de pagamento calculado inválido");
        }

        if (mensagemProcessadaRepository.registrarSeAusente(envelope.messageId(), Instant.now()) == 0) {
            return;
        }

        registrarPagamentoCalculado(payload.ticketId(), payload.pagamentoId(), payload.valor());
    }

    @Transactional
    public void processarPagamentoConfirmado(MensagemEnvelope<PagamentoConfirmadoPayload> envelope) {
        validador.validarEnvelope(envelope, TipoMensagem.PAGAMENTO_CONFIRMADO);

        PagamentoConfirmadoPayload payload = envelope.payload();

        validador.validarTicket(envelope, payload.ticketId());
        validador.validarIdentificador(payload.pagamentoId(), "pagamentoId");

        if (payload.status() != PagamentoStatus.PAGO) {
            throw new IllegalArgumentException("Status de confirmação de pagamento inválido");
        }

        if (payload.metodo() == null || payload.metodo().isBlank()) {
            throw new IllegalArgumentException("metodo é obrigatório");
        }

        if (mensagemProcessadaRepository.registrarSeAusente(envelope.messageId(), Instant.now()) == 0) {
            return;
        }

        confirmarPagamento(payload.ticketId(), payload.pagamentoId(), payload.valor());
    }

    @Transactional
    public TicketResponse registrarPagamentoCalculado(UUID ticketId, UUID pagamentoId, BigDecimal valor) {
        validarValor(valor);

        Ticket ticket = localizador.buscarParaAlteracao(ticketId);
        validarPagamento(ticket, pagamentoId);

        if (
            (ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO || ticket.getStatus() == TicketStatus.FINALIZADO)
            && ticket.getValor() != null
            && ticket.getValor().compareTo(valor) == 0
        ) {
            return TicketResponse.from(ticket);
        }

        if (ticket.getStatus() != TicketStatus.ATIVO || ticket.getSaida() == null) {
            throw new EstadoInvalidoException("O ticket não aguarda cálculo de pagamento");
        }

        ticket.registrarCalculo(pagamentoId, valor);

        if (ticket.getValorConfirmado() != null) {
            finalizar(ticket, ticket.getValorConfirmado());
        }

        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse confirmarPagamento(UUID ticketId, UUID pagamentoId, BigDecimal valorConfirmado) {
        validarValor(valorConfirmado);

        Ticket ticket = localizador.buscarParaAlteracao(ticketId);
        validarPagamento(ticket, pagamentoId);

        if (ticket.getStatus() == TicketStatus.ATIVO && ticket.getSaida() != null) {
            if (ticket.getValorConfirmado() != null && ticket.getValorConfirmado().compareTo(valorConfirmado) != 0) {
                throw new EstadoInvalidoException("Confirmações de pagamento divergentes");
            }

            ticket.registrarConfirmacaoAntecipada(pagamentoId, valorConfirmado);
            return TicketResponse.from(ticketRepository.save(ticket));
        }

        if (ticket.getStatus() == TicketStatus.FINALIZADO) {
            if (ticket.getValor() == null || ticket.getValor().compareTo(valorConfirmado) != 0) {
                throw new EstadoInvalidoException("O valor confirmado não corresponde ao ticket");
            }

            return TicketResponse.from(ticket);
        }

        if (ticket.getStatus() != TicketStatus.AGUARDANDO_PAGAMENTO) {
            throw new EstadoInvalidoException("O ticket não aguarda confirmação de pagamento");
        }

        ticket.vincularPagamento(pagamentoId);
        finalizar(ticket, valorConfirmado);

        return TicketResponse.from(ticketRepository.save(ticket));
    }

    private void finalizar(Ticket ticket, BigDecimal valorConfirmado) {
        if (ticket.getValor() == null || ticket.getValor().compareTo(valorConfirmado) != 0) {
            throw new EstadoInvalidoException("O valor confirmado não corresponde ao ticket");
        }

        if (ticket.getVagaId() == null) {
            throw new EstadoInvalidoException("O ticket não possui vaga associada");
        }

        ticket.finalizar(valorConfirmado);
        registroEvento.registrarLiberacao(ticket.getId(), ticket.getVagaId());
    }

    private void validarValor(BigDecimal valor) {
        if (
            valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2
            || valor.compareTo(new BigDecimal("99999999.99")) > 0
        ) {
            throw new IllegalArgumentException("Valor inválido: use um decimal não negativo com até duas casas");
        }
    }

    private void validarPagamento(Ticket ticket, UUID pagamentoId) {
        if (pagamentoId == null) {
            throw new IllegalArgumentException("pagamentoId é obrigatório");
        }

        if (ticket.getPagamentoId() != null && !ticket.getPagamentoId().equals(pagamentoId)) {
            throw new EstadoInvalidoException("Pagamento não corresponde ao ticket");
        }
    }
}
