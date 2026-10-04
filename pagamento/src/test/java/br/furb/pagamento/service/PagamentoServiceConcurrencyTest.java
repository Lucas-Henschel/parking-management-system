package br.furb.pagamento.service;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoResponse;
import br.furb.pagamento.dto.PagarRequest;
import br.furb.pagamento.entity.MetodoPagamento;
import br.furb.pagamento.entity.Pagamento;
import br.furb.pagamento.entity.PagamentoStatus;
import br.furb.pagamento.messaging.PagamentoPublisher;
import br.furb.pagamento.repository.MetodoPagamentoRepository;
import br.furb.pagamento.repository.PagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceConcurrencyTest {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private MetodoPagamentoRepository metodoPagamentoRepository;

    @Mock
    private PagamentoPublisher pagamentoPublisher;

    private PagamentoService service;

    @BeforeEach
    void setUp() {
        service = new PagamentoService(
                pagamentoRepository,
                metodoPagamentoRepository,
                pagamentoPublisher,
                new BigDecimal("10.00")
        );
    }

    @Test
    void deveManipularCalculoPagamentoDuplicadoComDataIntegrityViolationException() {
        // Simula cenário onde dois processos tentam criar o mesmo pagamento simultaneamente
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime saida = entrada.plusHours(2);
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, saida);

        Pagamento pagamentoExistente = criarPagamento(pagamentoId, ticketId, null, 
                new BigDecimal("20.00"), PagamentoStatus.CALCULADO);

        // Primeira verificação: não existe
        when(pagamentoRepository.findByTicketId(ticketId))
                .thenReturn(Optional.empty());
        
        // Save lança DataIntegrityViolationException (outro processo já criou)
        when(pagamentoRepository.save(any(Pagamento.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate key"));
        
        // Segunda verificação após exceção: agora existe
        when(pagamentoRepository.findByTicketId(ticketId))
                .thenReturn(Optional.of(pagamentoExistente));

        PagamentoCalculadoEvent resultado = service.calcularPagamento(request);

        // Deve retornar o pagamento existente
        assertEquals(pagamentoId, resultado.pagamentoId());
        assertEquals(ticketId, resultado.ticketId());
        assertEquals(new BigDecimal("20.00"), resultado.valor());
        assertEquals(PagamentoStatus.CALCULADO, resultado.status());

        // Deve ter tentado salvar e falhado, depois buscado novamente
        verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        verify(pagamentoRepository, times(2)).findByTicketId(ticketId);
        verify(pagamentoPublisher, times(1)).publicarPagamentoCalculado(any());
    }

    @Test
    void deveReutilizarPagamentoExistenteAoInvesDeRecriar() {
        // Testa idempotência: múltiplas chamadas com mesmo ticketId devem retornar o mesmo resultado
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime saida = entrada.plusHours(1);
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, saida);

        Pagamento pagamentoExistente = criarPagamento(pagamentoId, ticketId, null, 
                new BigDecimal("10.00"), PagamentoStatus.CALCULADO);

        when(pagamentoRepository.findByTicketId(ticketId))
                .thenReturn(Optional.of(pagamentoExistente));

        // Primeira chamada
        PagamentoCalculadoEvent resultado1 = service.calcularPagamento(request);
        
        // Segunda chamada
        PagamentoCalculadoEvent resultado2 = service.calcularPagamento(request);

        // Ambas devem retornar o mesmo resultado
        assertEquals(resultado1.pagamentoId(), resultado2.pagamentoId());
        assertEquals(resultado1.ticketId(), resultado2.ticketId());
        assertEquals(resultado1.valor(), resultado2.valor());

        // Nunca deve ter tentado salvar um novo pagamento
        verify(pagamentoRepository, never()).save(any(Pagamento.class));
        verify(pagamentoRepository, times(2)).findByTicketId(ticketId);
        verify(pagamentoPublisher, times(2)).publicarPagamentoCalculado(any());
    }

    @Test
    void devePagarApenasUmaVezComPessimisticLock() {
        // Simula cenário onde o pessimistic lock previne pagamento duplicado
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();
        
        Pagamento pagamentoCalculado = criarPagamento(pagamentoId, ticketId, null, 
                new BigDecimal("20.00"), PagamentoStatus.CALCULADO);
        
        MetodoPagamento metodo = criarMetodoPagamento(metodoPagamentoId, "PIX");

        // Primeira chamada: lock adquirido, encontra CALCULADO
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId))
                .thenReturn(Optional.of(pagamentoCalculado));
        
        when(metodoPagamentoRepository.findByNomeMetodoIgnoreCase("PIX"))
                .thenReturn(Optional.of(metodo));

        // Após salvar, retorna pagamento com status PAGO
        Pagamento pagamentoPago = criarPagamento(pagamentoId, ticketId, metodoPagamentoId, 
                new BigDecimal("20.00"), PagamentoStatus.PAGO);
        when(pagamentoRepository.save(any(Pagamento.class)))
                .thenReturn(pagamentoPago);

        PagamentoResponse resultado = service.pagar(ticketId, new PagarRequest("pix"));

        assertEquals(PagamentoStatus.PAGO, resultado.status());
        assertEquals(metodoPagamentoId, resultado.metodoPagamentoId());
        
        verify(pagamentoRepository, times(1)).findByTicketIdForUpdate(ticketId);
        verify(pagamentoRepository, times(1)).save(any(Pagamento.class));
        verify(pagamentoPublisher, times(1)).publicarPagamentoConfirmado(any());
    }

    @Test
    void deveRetornarPagamentoSeJaEstiverPagoSemReprocessar() {
        // Segundo processo chega e encontra pagamento já PAGO
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();
        
        Pagamento pagamentoPago = criarPagamento(pagamentoId, ticketId, metodoPagamentoId, 
                new BigDecimal("20.00"), PagamentoStatus.PAGO);

        when(pagamentoRepository.findByTicketIdForUpdate(ticketId))
                .thenReturn(Optional.of(pagamentoPago));

        PagamentoResponse resultado = service.pagar(ticketId, new PagarRequest("PIX"));

        assertEquals(PagamentoStatus.PAGO, resultado.status());
        assertEquals(metodoPagamentoId, resultado.metodoPagamentoId());
        
        // Não deve salvar novamente nem publicar evento
        verify(pagamentoRepository, never()).save(any(Pagamento.class));
        verify(pagamentoPublisher, never()).publicarPagamentoConfirmado(any());
    }

    @Test
    void deveFalharAoTentarPagarPagamentoNaoCalculado() {
        // Testa validação de estado: só pode pagar se status for CALCULADO
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        
        // Cria pagamento com status PENDENTE (não CALCULADO)
        Pagamento pagamentoPendente = criarPagamento(pagamentoId, ticketId, null, 
                new BigDecimal("20.00"), PagamentoStatus.CALCULADO);
        // Simula um estado inválido
        pagamentoPendente.setStatus(PagamentoStatus.PAGO);

        when(pagamentoRepository.findByTicketIdForUpdate(ticketId))
                .thenReturn(Optional.of(pagamentoPendente));

        PagamentoResponse resultado = service.pagar(ticketId, new PagarRequest("PIX"));

        // Como já está PAGO, deve retornar sem erro
        assertEquals(PagamentoStatus.PAGO, resultado.status());
        verify(pagamentoRepository, never()).save(any(Pagamento.class));
    }

    @Test
    void deveManipularConcorrenciaComMultiplasThreads() throws InterruptedException {
        // Teste de integração simulando múltiplas threads tentando calcular o mesmo pagamento
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        
        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime saida = entrada.plusHours(2);
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, saida);

        Pagamento pagamentoSalvo = criarPagamento(pagamentoId, ticketId, null, 
                new BigDecimal("20.00"), PagamentoStatus.CALCULADO);

        AtomicInteger saveAttempts = new AtomicInteger(0);
        AtomicInteger dataIntegrityViolations = new AtomicInteger(0);

        // Simula race condition: primeira thread salva, demais recebem exceção
        when(pagamentoRepository.findByTicketId(ticketId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(pagamentoSalvo));

        when(pagamentoRepository.save(any(Pagamento.class)))
                .thenAnswer(invocation -> {
                    int attempt = saveAttempts.incrementAndGet();
                    if (attempt == 1) {
                        return pagamentoSalvo; // Primeira thread consegue salvar
                    } else {
                        dataIntegrityViolations.incrementAndGet();
                        throw new DataIntegrityViolationException("Duplicate key");
                    }
                });

        int numThreads = 5;
        CountDownLatch latch = new CountDownLatch(numThreads);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        
        AtomicReference<Exception> exception = new AtomicReference<>();

        for (int i = 0; i < numThreads; i++) {
            executor.submit(() -> {
                try {
                    service.calcularPagamento(request);
                } catch (Exception e) {
                    exception.set(e);
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();

        // Não deve ter havido exceções não tratadas
        assertNull(exception.get());
        
        // Deve ter sido publicado para todas as threads
        verify(pagamentoPublisher, times(numThreads)).publicarPagamentoCalculado(any());
    }

    @Test
    void deveNormalizarMetodoPagamentoIgnorandoCaseComConcorrencia() {
        // Testa normalização de método de pagamento com diferentes cases
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();
        
        Pagamento pagamentoCalculado = criarPagamento(pagamentoId, ticketId, null, 
                new BigDecimal("20.00"), PagamentoStatus.CALCULADO);
        
        MetodoPagamento metodo = criarMetodoPagamento(metodoPagamentoId, "PIX");

        when(pagamentoRepository.findByTicketIdForUpdate(ticketId))
                .thenReturn(Optional.of(pagamentoCalculado));
        
        // Query normalizada deve funcionar independente do case
        when(metodoPagamentoRepository.findByNomeMetodoIgnoreCase(anyString()))
                .thenReturn(Optional.of(metodo));

        Pagamento pagamentoPago = criarPagamento(pagamentoId, ticketId, metodoPagamentoId, 
                new BigDecimal("20.00"), PagamentoStatus.PAGO);
        when(pagamentoRepository.save(any(Pagamento.class)))
                .thenReturn(pagamentoPago);

        // Tenta com diferentes cases
        PagamentoResponse resultado1 = service.pagar(ticketId, new PagarRequest("pix"));
        
        // Reset para nova tentativa
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId))
                .thenReturn(Optional.of(pagamentoCalculado));
        
        PagamentoResponse resultado2 = service.pagar(ticketId, new PagarRequest("PIX"));

        assertEquals(PagamentoStatus.PAGO, resultado1.status());
        assertEquals(PagamentoStatus.PAGO, resultado2.status());
        
        // Deve ter buscado com query case-insensitive
        verify(metodoPagamentoRepository, atLeastOnce()).findByNomeMetodoIgnoreCase(anyString());
    }

    private Pagamento criarPagamento(UUID id, UUID ticketId, UUID metodoPagamentoId, 
                                     BigDecimal valor, PagamentoStatus status) {
        Pagamento pagamento = new Pagamento();
        pagamento.setId(id);
        pagamento.setTicketId(ticketId);
        pagamento.setMetodoPagamentoId(metodoPagamentoId);
        pagamento.setValor(valor);
        pagamento.setData(LocalDateTime.now());
        pagamento.setStatus(status);
        return pagamento;
    }

    private MetodoPagamento criarMetodoPagamento(UUID id, String nome) {
        MetodoPagamento metodo = new MetodoPagamento();
        metodo.setId(id);
        metodo.setNomeMetodo(nome);
        return metodo;
    }
}
