package br.furb.pagamento.service;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoConfirmadoEvent;
import br.furb.pagamento.dto.PagamentoResponse;
import br.furb.pagamento.dto.PagarRequest;
import br.furb.pagamento.entity.MetodoPagamento;
import br.furb.pagamento.entity.Pagamento;
import br.furb.pagamento.entity.PagamentoStatus;
import br.furb.pagamento.exception.PagamentoNaoEncontradoException;
import br.furb.pagamento.messaging.PagamentoPublisher;
import br.furb.pagamento.repository.MetodoPagamentoRepository;
import br.furb.pagamento.repository.PagamentoRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PagamentoService {

    private final PagamentoRepository pagamentoRepository;
    private final MetodoPagamentoRepository metodoPagamentoRepository;
    private final PagamentoPublisher pagamentoPublisher;
    private final BigDecimal valorHora;

    public PagamentoService(
            PagamentoRepository pagamentoRepository,
            MetodoPagamentoRepository metodoPagamentoRepository,
            PagamentoPublisher pagamentoPublisher,
            @Value("${pagamento.valor-hora:10.00}") BigDecimal valorHora) {
        this.pagamentoRepository = pagamentoRepository;
        this.metodoPagamentoRepository = metodoPagamentoRepository;
        this.pagamentoPublisher = pagamentoPublisher;
        this.valorHora = valorHora;
    }

    @Transactional
    public PagamentoCalculadoEvent calcularPagamento(CalcularPagamentoRequest request) {

        if (!request.saida().isAfter(request.entrada())) {
            throw new IllegalArgumentException("A saída deve ser posterior à entrada");
        }
        if (valorHora.signum() <= 0) {
            throw new IllegalArgumentException("A tarifa por hora deve ser maior que zero");
        }

        Pagamento existente = pagamentoRepository.findByTicketId(request.ticketId()).orElse(null);

        if (existente != null) {
            PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
                    existente.getId(), existente.getTicketId(), existente.getValor(), existente.getData(), existente.getStatus()
            );
            pagamentoPublisher.publicarPagamentoCalculado(evento);
            return evento;
        }

        long minutos = Duration.between(request.entrada(), request.saida()).toMinutes();
        long horas = Math.max(1, (minutos + 59) / 60);
        BigDecimal valor = valorHora.multiply(BigDecimal.valueOf(horas)).setScale(2, RoundingMode.HALF_UP);

        Pagamento pagamento = new Pagamento();
        pagamento.setId(UUID.randomUUID());
        pagamento.setTicketId(request.ticketId());
        pagamento.setValor(valor);
        pagamento.setData(LocalDateTime.now());
        pagamento.setStatus(PagamentoStatus.CALCULADO);

        Pagamento salvo = pagamentoRepository.save(pagamento);

        PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
                salvo.getId(), salvo.getTicketId(), salvo.getValor(), salvo.getData(), salvo.getStatus()
        );
        pagamentoPublisher.publicarPagamentoCalculado(evento);
        return evento;
    }
    
    @Transactional
    public PagamentoResponse pagar(UUID ticketId, PagarRequest request) {
        Pagamento pagamento = pagamentoRepository.findByTicketId(ticketId)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(ticketId));
                
        if (pagamento.getStatus() == PagamentoStatus.PAGO) {
            return PagamentoResponse.from(pagamento);
        }
        
        MetodoPagamento metodo = metodoPagamentoRepository.findByNomeMetodo(request.metodo())
                .orElseThrow(() -> new IllegalArgumentException("Método de pagamento não encontrado"));
                
        pagamento.setMetodoPagamentoId(metodo.getId());
        pagamento.setStatus(PagamentoStatus.PAGO);
        
        Pagamento salvo = pagamentoRepository.save(pagamento);
        
        PagamentoConfirmadoEvent evento = new PagamentoConfirmadoEvent(
                salvo.getId(),
                salvo.getTicketId(),
                salvo.getValor(),
                metodo.getNomeMetodo(),
                salvo.getStatus()
        );
        pagamentoPublisher.publicarPagamentoConfirmado(evento);
        
        return PagamentoResponse.from(salvo);
    }

    public PagamentoResponse buscarPorId(UUID id) {
        return pagamentoRepository.findById(id).map(PagamentoResponse::from)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(id));
    }

    public List<PagamentoResponse> buscarPorTicket(UUID ticketId) {
        return pagamentoRepository.findAllByTicketIdOrderByDataDesc(ticketId)
                .stream().map(PagamentoResponse::from).toList();
    }
}
