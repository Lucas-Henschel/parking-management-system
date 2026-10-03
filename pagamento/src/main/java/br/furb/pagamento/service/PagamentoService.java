package br.furb.pagamento.service;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoResponse;
import br.furb.pagamento.entity.MetodoPagamento;
import br.furb.pagamento.entity.Pagamento;
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

    private static final String STATUS_PAGO = "PAGO";

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
    public PagamentoCalculadoEvent calcularPagamento(
            CalcularPagamentoRequest request) {

        validarRequest(request);

        MetodoPagamento metodo =
                metodoPagamentoRepository.findById(
                        request.metodoPagamentoId()
                ).orElseThrow(() ->
                        new IllegalArgumentException(
                                "Método de pagamento não encontrado"
                        )
                );

        Pagamento existente =
                pagamentoRepository.findByTicketId(
                        request.ticketId()
                ).orElse(null);

        if (existente != null) {
            PagamentoCalculadoEvent evento =
                    criarEvento(existente);

            pagamentoPublisher.publicarPagamentoCalculado(evento);

            return evento;
        }

        long horas =
                calcularHorasCobradas(
                        request.entrada(),
                        request.saida()
                );

        BigDecimal valor = valorHora
                .multiply(BigDecimal.valueOf(horas))
                .setScale(2, RoundingMode.HALF_UP);

        Pagamento pagamento = new Pagamento();

        pagamento.setId(UUID.randomUUID());
        pagamento.setTicketId(request.ticketId());
        pagamento.setMetodoPagamentoId(metodo.getId());
        pagamento.setValor(valor);
        pagamento.setData(LocalDateTime.now());
        pagamento.setStatus(STATUS_PAGO);

        Pagamento salvo =
                pagamentoRepository.save(pagamento);

        PagamentoCalculadoEvent evento =
                criarEvento(salvo);

        pagamentoPublisher.publicarPagamentoCalculado(evento);

        return evento;
    }

    public PagamentoResponse buscarPorId(UUID id) {
        return pagamentoRepository.findById(id)
                .map(PagamentoResponse::from)
                .orElseThrow(() ->
                        new PagamentoNaoEncontradoException(id)
                );
    }

    public List<PagamentoResponse> buscarPorTicket(UUID ticketId) {
        return pagamentoRepository
                .findAllByTicketIdOrderByDataDesc(ticketId)
                .stream()
                .map(PagamentoResponse::from)
                .toList();
    }

    private void validarRequest(
            CalcularPagamentoRequest request) {

        if (!request.saida().isAfter(request.entrada())) {
            throw new IllegalArgumentException(
                    "A saída deve ser posterior à entrada"
            );
        }

        if (valorHora.signum() <= 0) {
            throw new IllegalArgumentException(
                    "A tarifa por hora deve ser maior que zero"
            );
        }
    }

    private long calcularHorasCobradas(
            LocalDateTime entrada,
            LocalDateTime saida) {

        long minutos =
                Duration.between(entrada, saida).toMinutes();

        return Math.max(1, (minutos + 59) / 60);
    }

    private PagamentoCalculadoEvent criarEvento(
            Pagamento pagamento) {

        return new PagamentoCalculadoEvent(
                pagamento.getId(),
                pagamento.getTicketId(),
                pagamento.getValor(),
                pagamento.getData(),
                pagamento.getStatus()
        );
    }
}