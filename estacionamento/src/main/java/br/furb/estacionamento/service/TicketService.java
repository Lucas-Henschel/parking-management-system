package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TicketResponse;
import br.furb.estacionamento.dto.CalcularPagamentoPayload;
import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.entity.Veiculo;
import br.furb.estacionamento.enums.Recurso;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import br.furb.estacionamento.exception.RecursoNaoEncontradoException;
import br.furb.estacionamento.exception.TicketEmUsoException;
import br.furb.estacionamento.repository.TicketRepository;
import br.furb.estacionamento.repository.VeiculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
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
    private final TicketLocalizador localizador;
    private final RegistroEventoService registroEvento;

    public TicketService(
        TicketRepository ticketRepository,
        VeiculoRepository veiculoRepository,
        TicketLocalizador localizador,
        RegistroEventoService registroEvento
    ) {
        this.ticketRepository = ticketRepository;
        this.veiculoRepository = veiculoRepository;
        this.localizador = localizador;
        this.registroEvento = registroEvento;
    }

    @Transactional
    public TicketResponse registrarEntrada(String placaInformada) {
        String placa = placaInformada.trim().toUpperCase(Locale.ROOT);
        Veiculo veiculo = veiculoRepository.findByPlaca(placa)
            .orElseGet(() -> veiculoRepository.save(new Veiculo(placa)));

        if (ticketRepository.existsByVeiculo_IdAndStatusIn(veiculo.getId(), STATUS_TICKET_ABERTO)) {
            throw new TicketEmUsoException();
        }

        Ticket ticket = ticketRepository.save(new Ticket(veiculo, Instant.now()));
        registroEvento.registrarReserva(ticket.getId());

        return TicketResponse.from(ticket);
    }

    @Transactional(readOnly = true)
    public TicketResponse buscarPorId(UUID ticketId) {
        Ticket ticket = ticketRepository.findById(ticketId)
            .orElseThrow(() -> new RecursoNaoEncontradoException(Recurso.TICKET));

        return TicketResponse.from(ticket);
    }

    @Transactional
    public TicketResponse registrarSaida(UUID ticketId) {
        Ticket ticket = localizador.buscarParaAlteracao(ticketId);

        if (
            ticket.getSaida() != null && (ticket.getStatus() == TicketStatus.ATIVO
            || ticket.getStatus() == TicketStatus.AGUARDANDO_PAGAMENTO || ticket.getStatus() == TicketStatus.FINALIZADO)
        ) {
            return TicketResponse.from(ticket);
        }

        if (ticket.getStatus() != TicketStatus.ATIVO || ticket.getSaida() != null) {
            throw new EstadoInvalidoException("O ticket não está disponível para saída");
        }

        ticket.registrarSaida(Instant.now());
        Ticket salvo = ticketRepository.save(ticket);

        CalcularPagamentoPayload payload = new CalcularPagamentoPayload(
            salvo.getId(), salvo.getEntrada(), salvo.getSaida()
        );
        registroEvento.registrarCalculoPagamento(payload);

        return TicketResponse.from(salvo);
    }
}
