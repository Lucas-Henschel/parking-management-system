package br.furb.pagamento.service;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoConfirmadoEvent;
import br.furb.pagamento.dto.PagamentoResponse;
import br.furb.pagamento.dto.PagarRequest;
import br.furb.pagamento.entity.MetodoPagamento;
import br.furb.pagamento.entity.Pagamento;
import br.furb.pagamento.enums.PagamentoStatus;
import br.furb.pagamento.exception.MetodoPagamentoInvalidoException;
import br.furb.pagamento.exception.PagamentoNaoEncontradoException;
import br.furb.pagamento.exception.PeriodoInvalidoException;
import br.furb.pagamento.messaging.PagamentoPublisher;
import br.furb.pagamento.repository.MensagemProcessadaRepository;
import br.furb.pagamento.repository.MetodoPagamentoRepository;
import br.furb.pagamento.repository.PagamentoRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PagamentoService {
    private final PagamentoRepository pagamentoRepository;
    private final MetodoPagamentoRepository metodoPagamentoRepository;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;

    private final PagamentoPublisher pagamentoPublisher;

    private final BigDecimal valorHora;

    public PagamentoService(
        PagamentoRepository pagamentoRepository,
        MetodoPagamentoRepository metodoPagamentoRepository,
        MensagemProcessadaRepository mensagemProcessadaRepository,
        PagamentoPublisher pagamentoPublisher,
        @Value("${pagamento.valor-hora:10.00}") BigDecimal valorHora
    ) {
        this.pagamentoRepository = pagamentoRepository;
        this.metodoPagamentoRepository = metodoPagamentoRepository;
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
        this.pagamentoPublisher = pagamentoPublisher;
        this.valorHora = valorHora;
    }

    @Transactional
    public PagamentoCalculadoEvent calcularPagamento(CalcularPagamentoRequest request) {
        if (!request.saida().isAfter(request.entrada())) {
            throw new PeriodoInvalidoException();
        }

        if (valorHora.signum() <= 0) {
            throw new IllegalArgumentException("A tarifa por hora deve ser maior que zero");
        }

        long minutos = Duration.between(request.entrada(), request.saida()).toMinutes();
        long horas = Math.max(1, (minutos + 59) / 60);

        BigDecimal valor = valorHora.multiply(BigDecimal.valueOf(horas)).setScale(2, RoundingMode.HALF_UP);
        pagamentoRepository.inserirCalculadoSeAusente(UUID.randomUUID(), request.ticketId(), valor, Instant.now());

        Pagamento pagamento = pagamentoRepository.findByTicketId(request.ticketId())
            .orElseThrow(() -> new IllegalStateException("Pagamento não encontrado após cálculo"));

        PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
            pagamento.getId(),
            pagamento.getTicketId(),
            pagamento.getValor(),
            pagamento.getData(),
            pagamento.getStatus()
        );

        publicarAposCommit(() -> pagamentoPublisher.publicarPagamentoCalculado(evento));
        return evento;
    }

    /**
     * Processa a mensagem de forma idempotente: o messageId é registrado na mesma transação do
     * cálculo; se já existia, a mensagem é duplicada e é ignorada.
     */
    @Transactional
    public void processarCalculo(MensagemEnvelope<CalcularPagamentoRequest> envelope) {
        if (mensagemProcessadaRepository.registrarSeAusente(envelope.messageId(), Instant.now()) == 0) {
            return;
        }

        calcularPagamento(envelope.payload());
    }

    @Transactional
    public PagamentoResponse pagar(UUID ticketId, PagarRequest request) {
        Pagamento pagamento = pagamentoRepository.findByTicketIdForUpdate(ticketId)
            .orElseThrow(() -> new PagamentoNaoEncontradoException(ticketId));
                
        if (pagamento.getStatus() == PagamentoStatus.PAGO) {
            return PagamentoResponse.from(pagamento);
        }
        
        String metodoNormalizado = request.metodo().toUpperCase();
        MetodoPagamento metodo = metodoPagamentoRepository.findByNomeMetodoIgnoreCase(metodoNormalizado)
            .orElseThrow(() -> new MetodoPagamentoInvalidoException(request.metodo()));
                
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
