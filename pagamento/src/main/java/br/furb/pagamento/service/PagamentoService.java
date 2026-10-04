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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
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
            publicarAposCommit(() -> pagamentoPublisher.publicarPagamentoCalculado(evento));
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

        try {
            Pagamento salvo = pagamentoRepository.save(pagamento);

            PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
                    salvo.getId(), salvo.getTicketId(), salvo.getValor(), salvo.getData(), salvo.getStatus()
            );
            publicarAposCommit(() -> pagamentoPublisher.publicarPagamentoCalculado(evento));
            return evento;
        } catch (DataIntegrityViolationException e) {
            // Violação de UNIQUE constraint em ticket_id - outro processo já criou o pagamento
            // Recarregar o pagamento existente e republicar o evento
            Pagamento pagamentoExistente = pagamentoRepository.findByTicketId(request.ticketId())
                    .orElseThrow(() -> new IllegalStateException("Pagamento não encontrado após violação de constraint"));
            
            PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
                    pagamentoExistente.getId(), 
                    pagamentoExistente.getTicketId(), 
                    pagamentoExistente.getValor(), 
                    pagamentoExistente.getData(), 
                    pagamentoExistente.getStatus()
            );
            publicarAposCommit(() -> pagamentoPublisher.publicarPagamentoCalculado(evento));
            return evento;
        }
    }
    
    @Transactional
    public PagamentoResponse pagar(UUID ticketId, PagarRequest request) {
        Pagamento pagamento = pagamentoRepository.findByTicketIdForUpdate(ticketId)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(ticketId));
                
        if (pagamento.getStatus() == PagamentoStatus.PAGO) {
            return PagamentoResponse.from(pagamento);
        }
        
        if (pagamento.getStatus() != PagamentoStatus.CALCULADO) {
            throw new IllegalStateException("Pagamento deve estar no status CALCULADO para ser pago");
        }
        
        String metodoNormalizado = request.metodo().toUpperCase();
        MetodoPagamento metodo = metodoPagamentoRepository.findByNomeMetodoIgnoreCase(metodoNormalizado)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Método de pagamento '%s' não encontrado", request.metodo())));
                
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
        publicarAposCommit(() -> pagamentoPublisher.publicarPagamentoConfirmado(evento));
        
        return PagamentoResponse.from(salvo);
    }

    public PagamentoResponse buscarPorId(UUID id) {
        return pagamentoRepository.findById(id).map(PagamentoResponse::from)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(id));
    }

    public PagamentoResponse buscarPorTicket(UUID ticketId) {
        return pagamentoRepository.findByTicketId(ticketId)
                .map(PagamentoResponse::from)
                .orElseThrow(() -> new PagamentoNaoEncontradoException(ticketId));
    }
    
    private void publicarAposCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }
}
