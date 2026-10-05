package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.TicketStatus;
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

    private static final List<TicketStatus> STATUS_TICKET_ABERTO = List.of(
            TicketStatus.PENDENTE,
            TicketStatus.ATIVO,
            TicketStatus.AGUARDANDO_PAGAMENTO
    );

    private final TicketRepository ticketRepository;
    private final VeiculoRepository veiculoRepository;

    public TicketService(TicketRepository ticketRepository, VeiculoRepository veiculoRepository) {
        this.ticketRepository = ticketRepository;
        this.veiculoRepository = veiculoRepository;
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
        if (ticket.getStatus() == TicketStatus.ATIVO && Objects.equals(ticket.getVagaId(), vagaId)) {
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
    public TicketResponse registrarVagaIndisponivel(UUID ticketId) {
        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getStatus() == TicketStatus.RECUSADO) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.PENDENTE) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda reserva de vaga");
        }

        ticket.setStatus(TicketStatus.RECUSADO);
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse registrarSaida(UUID ticketId) {
        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getStatus() != TicketStatus.ATIVO || ticket.getSaida() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não está disponível para saída");
        }

        ticket.setSaida(Instant.now());
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse registrarPagamentoCalculado(UUID ticketId, BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor do pagamento deve ser não negativo");
        }

        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO
                && ticket.getValor() != null
                && ticket.getValor().compareTo(valor) == 0) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.ATIVO || ticket.getSaida() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda cálculo de pagamento");
        }

        ticket.setValor(valor);
        ticket.setStatus(TicketStatus.AGUARDANDO_PAGAMENTO);
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    @Transactional
    public TicketResponse confirmarPagamento(UUID ticketId) {
        Ticket ticket = buscarParaAtualizar(ticketId);
        if (ticket.getStatus() == TicketStatus.FINALIZADO) {
            return TicketResponse.from(ticket);
        }
        if (ticket.getStatus() != TicketStatus.AGUARDANDO_PAGAMENTO) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O ticket não aguarda confirmação de pagamento");
        }

        ticket.setStatus(TicketStatus.FINALIZADO);
        return TicketResponse.from(ticketRepository.save(ticket));
    }

    private Ticket buscarParaAtualizar(UUID ticketId) {
        return ticketRepository.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket não encontrado"));
    }
}