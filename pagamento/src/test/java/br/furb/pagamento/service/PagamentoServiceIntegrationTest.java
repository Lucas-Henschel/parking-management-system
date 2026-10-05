package br.furb.pagamento.service;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.dto.PagarRequest;
import br.furb.pagamento.enums.PagamentoStatus;
import br.furb.pagamento.messaging.PagamentoPublisher;
import br.furb.pagamento.repository.MensagemProcessadaRepository;
import br.furb.pagamento.repository.PagamentoRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Testes contra um PostgreSQL real: exercitam o lock pessimista, o UNIQUE em ticket_id e a
 * idempotência por messageId, que não podem ser validados com mocks.
 */
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
@Testcontainers
class PagamentoServiceIntegrationTest {

    private static final int THREADS = 8;

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired
    private PagamentoService service;

    @Autowired
    private PagamentoRepository pagamentoRepository;

    @Autowired
    private MensagemProcessadaRepository mensagemProcessadaRepository;

    @MockitoBean
    private PagamentoPublisher publisher;

    private CalcularPagamentoRequest requisicao(UUID ticketId) {
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        return new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(90, ChronoUnit.MINUTES));
    }

    private <T> List<Future<T>> emParalelo(Callable<T> tarefa) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        CountDownLatch largada = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            futuros.add(executor.submit(() -> {
                largada.await();
                return tarefa.call();
            }));
        }
        largada.countDown();
        executor.shutdown();
        return futuros;
    }

    @Test
    void calcularConcorrenteCriaUmUnicoPagamentoEPublicaParaTodos() throws Exception {
        UUID ticketId = UUID.randomUUID();

        for (Future<?> f : emParalelo(() -> service.calcularPagamento(requisicao(ticketId)))) {
            f.get();
        }

        assertEquals(1, pagamentoRepository.findAll().stream().filter(p -> p.getTicketId().equals(ticketId)).count());
        verify(publisher, times(THREADS)).publicarPagamentoCalculado(any());
    }

    @Test
    void pagarConcorrenteConfirmaEPublicaUmaUnicaVez() throws Exception {
        UUID ticketId = UUID.randomUUID();
        service.calcularPagamento(requisicao(ticketId));

        for (Future<?> f : emParalelo(() -> service.pagar(ticketId, new PagarRequest("pix")))) {
            assertEquals(PagamentoStatus.PAGO, ((br.furb.pagamento.dto.PagamentoResponse) f.get()).status());
        }

        verify(publisher, times(1)).publicarPagamentoConfirmado(any());
        assertEquals(PagamentoStatus.PAGO, pagamentoRepository.findByTicketId(ticketId).orElseThrow().getStatus());
    }

    @Test
    void mensagemComMesmoMessageIdEProcessadaUmaUnicaVez() throws Exception {
        UUID ticketId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MensagemEnvelope<CalcularPagamentoRequest> envelope = new MensagemEnvelope<>(
                messageId, ticketId, "CALCULAR_PAGAMENTO", Instant.now(), requisicao(ticketId));

        for (Future<?> f : emParalelo(() -> {
            service.processarCalculo(envelope);
            return null;
        })) {
            f.get();
        }

        assertTrue(mensagemProcessadaRepository.existsById(messageId));
        verify(publisher, times(1)).publicarPagamentoCalculado(any());
    }

    @Test
    void erroNoCalculoDesfazORegistroDaMensagemParaPermitirReentrega() {
        UUID messageId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Instant agora = Instant.now();
        MensagemEnvelope<CalcularPagamentoRequest> invalida = new MensagemEnvelope<>(
                messageId, ticketId, "CALCULAR_PAGAMENTO", agora,
                new CalcularPagamentoRequest(ticketId, agora, agora.minus(1, ChronoUnit.HOURS)));

        try {
            service.processarCalculo(invalida);
        } catch (IllegalArgumentException esperado) {
            // saída anterior à entrada
        }

        assertTrue(mensagemProcessadaRepository.findById(messageId).isEmpty());
    }
}
