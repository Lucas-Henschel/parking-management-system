package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.dto.CalcularPagamentoPayload;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.messaging.TicketPublisher;
import br.furb.estacionamento.repository.TicketRepository;
import br.furb.estacionamento.repository.VeiculoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

@Service
public class TicketService {

    private static final int MAXIMO_TENTATIVAS_RESERVA = 3;

    private static final List<TicketStatus> STATUS_TICKET_ABERTO = List.of(
            TicketStatus.PENDENTE,
            TicketStatus.ATIVO,
            TicketStatus.AGUARDANDO_PAGAMENTO
    );

    private final TicketRepository ticketRepository;
    private final VeiculoRepository veiculoRepository;
    private final TicketPublisher ticketPublisher;

    public TicketService(
            TicketRepository ticketRepository,
            VeiculoRepository veiculoRepository,
            TicketPublisher ticketPublisher
    ) {
        this.ticketRepository = ticketRepository;
        this.veiculoRepository = veiculoRepository;
        this.ticketPublisher = ticketPublisher;
    }

    @Transactional
    public TicketResponse registrarEntrada(String placaInformada) {
        String placa = placaInformada.trim().toUpperCase(Locale.ROOT);
        Veiculo veiculo = veiculoRepository.findByPlaca(placa)
                .orElseGet(() -> veiculoRepository.save(new Veiculo(placa)));

        if (ticketRepository.existsByVeiculo_IdAndStatusIn(veiculo.getId(), STATUS_TICKET_ABERTO)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O veículo já possui um ticket em aberto");
        }

        Ticket ticket = ticketRepository.save(new Ticket(veiculo, Instant.now()));
        ticketPublisher.publicarReserva(ticket.getId());
        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse buscarPorId(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket não encontrado"));
        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse registrarVagaConfirmada(UUID ticketId, UUID vagaId) {
        if (vagaId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "vagaId é obrigatório");
        }

        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getStatus() != TicketStatus.PENDENTE && Objects.equals(ticket.getVagaId(), vagaId)) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda reserva de vaga");
        }

        ticket.setVagaId(vagaId);
        ticket.setStatus(TicketStatus.ATIVO);
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse registrarVagaIndisponivel(UUID ticketId, String motivo) {
        if (!"SEM_VAGAS".equals(motivo) && !"TICKET_FINALIZADO".equals(motivo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Motivo de indisponibilidade inválido");
        }
        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getStatus() == TicketStatus.RECUSADO) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda reserva de vaga");
        }

        if ("SEM_VAGAS".equals(motivo) && ticket.getTentativasReserva() < MAXIMO_TENTATIVAS_RESERVA) {
            ticket.registrarNovaTentativaReserva();
            ticketPublisher.publicarReserva(ticket.getId());
        } else {
            ticket.setStatus(TicketStatus.RECUSADO);
            ticket.setSaida(Instant.now());
        }
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse registrarSaida(UUID ticketId) {
        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getSaida() != null && (ticket.getStatus() == TicketStatus.ATIVO
                || ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO || ticket.getStatus() == TicketStatus.FINALIZADO)) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.ATIVO || ticket.getSaida() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não está disponível para saída");
        }

        ticket.setSaida(Instant.now());
        Ticket salvo = ticketRepository.save(ticket);
        CalcularPagamentoPayload payload = new CalcularPagamentoPayload(
            salvo.getId(), salvo.getEntrada(), salvo.getSaida());
        ticketPublisher.publicarCalculoPagamento(payload);
        return TicketResponse.from(salvo);
    }

    @Transactional
    public TicketResponse registrarPagamentoCalculado(UUID ticketId, UUID pagamentoId, BigDecimal valor) {
        validarValor(valor);
        Ticket ticket = buscarParaAtualizar(ticketId);
        validarPagamento(ticket, pagamentoId);
        if ((ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO || ticket.getStatus() == TicketStatus.FINALIZADO)
                && ticket.getValor() != null
                && ticket.getValor().compareTo(valor) == 0) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.ATIVO || ticket.getSaida() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda cálculo de pagamento");
        }

        ticket.setValor(valor);
        ticket.setPagamentoId(pagamentoId);
        ticket.setStatus(TicketStatus.AGUARDANDO_PAGAMENTO);
        if (ticket.getValorConfirmado() != null) {
            finalizar(ticket, ticket.getValorConfirmado());
        }
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse confirmarPagamento(UUID ticketId, UUID pagamentoId, BigDecimal valorConfirmado) {
        validarValor(valorConfirmado);
        Ticket ticket = buscarParaAtualizar(ticketId);
        validarPagamento(ticket, pagamentoId);
        if (ticket.getStatus() == TicketStatus.ATIVO && ticket.getSaida() != null) {
            if (ticket.getValorConfirmado() != null && ticket.getValorConfirmado().compareTo(valorConfirmado) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Confirmações de pagamento divergentes");
            }
            ticket.setPagamentoId(pagamentoId);
            ticket.setValorConfirmado(valorConfirmado);
            return TicketResponse.from(ticketRepository.save(ticket));
        }
        if (ticket.getStatus() == TicketStatus.FINALIZADO) {
            if (ticket.getValor() == null || ticket.getValor().compareTo(valorConfirmado) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "O valor confirmado não corresponde ao ticket");
            }
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.AGUARDANDO_PAGAMENTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda confirmação de pagamento");
        }
        ticket.setPagamentoId(pagamentoId);
        finalizar(ticket, valorConfirmado);
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    private void finalizar(Ticket ticket, BigDecimal valorConfirmado) {
        if (ticket.getValor() == null || ticket.getValor().compareTo(valorConfirmado) != 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O valor confirmado não corresponde ao ticket");
        }
        if (ticket.getVagaId() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não possui vaga associada");
        }

        ticket.setStatus(TicketStatus.FINALIZADO);
        ticket.setValorConfirmado(valorConfirmado);
        ticketPublisher.publicarLiberacao(ticket.getId(), ticket.getVagaId());
    }

    private Ticket buscarParaAtualizar(UUID ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket não encontrado"));
    }

    private void validarValor(BigDecimal valor) {
        if (valor == null || valor.signum() < 0 || valor.stripTrailingZeros().scale() > 2
                || valor.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor inválido: use um decimal não negativo com até duas casas");
        }
    }

    private void validarPagamento(Ticket ticket, UUID pagamentoId) {
        if (pagamentoId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pagamentoId é obrigatório");
        }
        if (ticket.getPagamentoId() != null && !ticket.getPagamentoId().equals(pagamentoId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Pagamento não corresponde ao ticket");
        }
    }
}
