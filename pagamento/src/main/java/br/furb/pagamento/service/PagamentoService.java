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
import br.furb.pagamento.repository.MensagemProcessadaRepository;
import br.furb.pagamento.repository.MetodoPagamentoRepository;
import br.furb.pagamento.repository.PagamentoRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class PagamentoService {
    private static final String TIPO_CALCULAR_PAGAMENTO = "CALCULAR_PAGAMENTO";

    private final PagamentoRepository pagamentoRepository;
    private final MetodoPagamentoRepository metodoPagamentoRepository;
    private final MensagemProcessadaRepository mensagemProcessadaRepository;

    private final RegistroEventoService registroEventoService;

    private final BigDecimal valorHora;

    public PagamentoService(
        PagamentoRepository pagamentoRepository,
        MetodoPagamentoRepository metodoPagamentoRepository,
        MensagemProcessadaRepository mensagemProcessadaRepository,
        RegistroEventoService registroEventoService,
        @Value("${pagamento.valor-hora:10.00}") BigDecimal valorHora
    ) {
        this.pagamentoRepository = pagamentoRepository;
        this.metodoPagamentoRepository = metodoPagamentoRepository;
        this.mensagemProcessadaRepository = mensagemProcessadaRepository;
        this.registroEventoService = registroEventoService;
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

        long segundos = Duration.between(request.entrada(), request.saida()).toSeconds();
        long horas = Math.max(1, (segundos + 3599) / 3600);

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

        registroEventoService.registrarPagamentoCalculado(evento);
        return evento;
    }

    /**
     * Processa a mensagem de forma idempotente: o messageId é registrado na mesma transação do
     * cálculo; se já existia, a mensagem é duplicada e é ignorada.
     */
    @Transactional
    public void processarCalculo(MensagemEnvelope<CalcularPagamentoRequest> envelope) {
        validarEnvelope(envelope);

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

        registroEventoService.registrarPagamentoConfirmado(evento);
        
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

    /**
     * Dados inválidos lançam IllegalArgumentException, que não é retentado e vai direto para a DLQ.
     */
    private void validarEnvelope(MensagemEnvelope<CalcularPagamentoRequest> envelope) {
        if (envelope == null
            || envelope.messageId() == null
            || envelope.correlationId() == null
            || envelope.payload() == null
            || !TIPO_CALCULAR_PAGAMENTO.equals(envelope.tipo())) {
            throw new IllegalArgumentException("Envelope inválido para " + TIPO_CALCULAR_PAGAMENTO + ".");
        }

        if (!envelope.correlationId().equals(envelope.payload().ticketId())) {
            throw new IllegalArgumentException("correlationId deve ser igual ao ticketId.");
        }
    }
}
