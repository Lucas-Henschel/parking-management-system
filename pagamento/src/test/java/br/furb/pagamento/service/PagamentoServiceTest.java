package br.furb.pagamento.service;

import br.furb.pagamento.dto.*;
import br.furb.pagamento.entity.MetodoPagamento;
import br.furb.pagamento.entity.Pagamento;
import br.furb.pagamento.enums.PagamentoStatus;
import br.furb.pagamento.exception.MetodoPagamentoInvalidoException;
import br.furb.pagamento.exception.PagamentoNaoEncontradoException;
import br.furb.pagamento.exception.PeriodoInvalidoException;
import br.furb.pagamento.repository.MetodoPagamentoRepository;
import br.furb.pagamento.repository.PagamentoRepository;
import br.furb.pagamento.repository.MensagemProcessadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoServiceTest {

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private MetodoPagamentoRepository metodoPagamentoRepository;

    @Mock
    private RegistroEventoService registroEventoService;

    @Mock
    private MensagemProcessadaRepository mensagemProcessadaRepository;

    private PagamentoService service;

    @BeforeEach
    void setUp() {
        service = new PagamentoService(
                pagamentoRepository,
                metodoPagamentoRepository,
                mensagemProcessadaRepository,
                registroEventoService,
                new BigDecimal("10.00")
        );
    }

    private void cenarioCalculo(UUID ticketId, BigDecimal valor) {
        when(pagamentoRepository.findByTicketId(ticketId)).thenReturn(
                Optional.of(pagamento(UUID.randomUUID(), ticketId, null, valor, PagamentoStatus.CALCULADO)));
    }

    private BigDecimal valorGravado(UUID ticketId) {
        ArgumentCaptor<BigDecimal> captor = ArgumentCaptor.forClass(BigDecimal.class);
        verify(pagamentoRepository).inserirCalculadoSeAusente(any(), eq(ticketId), captor.capture(), any());
        return captor.getValue();
    }

    @Test
    void deveCalcularPagamentoDeUmaHora() {
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        cenarioCalculo(ticketId, new BigDecimal("10.00"));

        PagamentoCalculadoEvent resultado = service.calcularPagamento(
                new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(30, ChronoUnit.MINUTES)));

        assertEquals(new BigDecimal("10.00"), valorGravado(ticketId));
        assertEquals(ticketId, resultado.ticketId());
        assertEquals(PagamentoStatus.CALCULADO, resultado.status());

        verify(registroEventoService).registrarPagamentoCalculado(resultado);
    }

    @Test
    void deveCobrarDuasHorasQuandoUltrapassarUmaHora() {
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        cenarioCalculo(ticketId, new BigDecimal("20.00"));

        service.calcularPagamento(new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(61, ChronoUnit.MINUTES)));

        assertEquals(new BigDecimal("20.00"), valorGravado(ticketId));
    }

    @Test
    void deveCobrarHoraAdicionalQuandoSoSobraremSegundos() {
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        cenarioCalculo(ticketId, new BigDecimal("20.00"));

        service.calcularPagamento(new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(3601, ChronoUnit.SECONDS)));

        assertEquals(new BigDecimal("20.00"), valorGravado(ticketId));
    }

    @Test
    void deveCobrarUmaHoraQuandoPermanecerExatamenteUmaHora() {
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        cenarioCalculo(ticketId, new BigDecimal("10.00"));

        service.calcularPagamento(new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(60, ChronoUnit.MINUTES)));

        assertEquals(new BigDecimal("10.00"), valorGravado(ticketId));
    }

    @Test
    void deveRejeitarEnvelopeInvalidoSemRegistrarAMensagem() {
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        CalcularPagamentoRequest payload = new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(1, ChronoUnit.HOURS));

        assertThrows(IllegalArgumentException.class, () -> service.processarCalculo(null));
        assertThrows(IllegalArgumentException.class, () -> service.processarCalculo(
                new MensagemEnvelope<>(UUID.randomUUID(), ticketId, "CALCULAR_PAGAMENTO", Instant.now(), null)));
        assertThrows(IllegalArgumentException.class, () -> service.processarCalculo(
                new MensagemEnvelope<>(UUID.randomUUID(), ticketId, "OUTRO_TIPO", Instant.now(), payload)));
        assertThrows(IllegalArgumentException.class, () -> service.processarCalculo(
                new MensagemEnvelope<>(UUID.randomUUID(), UUID.randomUUID(), "CALCULAR_PAGAMENTO", Instant.now(), payload)));

        verifyNoInteractions(mensagemProcessadaRepository, pagamentoRepository, registroEventoService);
    }

    @Test
    void deveRejeitarSaidaAnteriorOuIgualAEntrada() {
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(UUID.randomUUID(), entrada, entrada);

        PeriodoInvalidoException exception = assertThrows(
                PeriodoInvalidoException.class,
                () -> service.calcularPagamento(request)
        );

        assertEquals("A saída deve ser posterior à entrada", exception.getMessage());
        verifyNoInteractions(metodoPagamentoRepository, pagamentoRepository, registroEventoService);
    }

    @Test
    void deveIgnorarMensagemDuplicadaSemCalcular() {
        UUID messageId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        when(mensagemProcessadaRepository.registrarSeAusente(eq(messageId), any())).thenReturn(0);

        service.processarCalculo(new MensagemEnvelope<>(messageId, ticketId, "CALCULAR_PAGAMENTO", Instant.now(),
                new CalcularPagamentoRequest(ticketId, Instant.now().minus(1, ChronoUnit.HOURS), Instant.now())));

        verifyNoInteractions(pagamentoRepository, registroEventoService);
    }

    @Test
    void deveCalcularQuandoMensagemForNova() {
        UUID messageId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        Instant entrada = Instant.parse("2026-10-03T10:00:00Z");
        when(mensagemProcessadaRepository.registrarSeAusente(eq(messageId), any())).thenReturn(1);
        cenarioCalculo(ticketId, new BigDecimal("10.00"));

        service.processarCalculo(new MensagemEnvelope<>(messageId, ticketId, "CALCULAR_PAGAMENTO", Instant.now(),
                new CalcularPagamentoRequest(ticketId, entrada, entrada.plus(10, ChronoUnit.MINUTES))));

        verify(pagamentoRepository).inserirCalculadoSeAusente(any(), eq(ticketId), any(), any());
    }

    @Test
    void devePagarComSucesso() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();
        
        Pagamento existente = pagamento(pagamentoId, ticketId, null, new BigDecimal("20.00"), PagamentoStatus.CALCULADO);
        MetodoPagamento metodo = metodo(metodoPagamentoId, "PIX");
        
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.of(existente));
        when(metodoPagamentoRepository.findByNomeMetodoIgnoreCase("PIX")).thenReturn(Optional.of(metodo));
        
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PagamentoResponse response = service.pagar(ticketId, new PagarRequest("PIX"));
        
        assertEquals(PagamentoStatus.PAGO, response.status());
        assertEquals(metodoPagamentoId, response.metodoPagamentoId());

        verify(registroEventoService).registrarPagamentoConfirmado(any());
    }
    
    @Test
    void deveRetornarMesmoPagamentoSeJaEstiverPago() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();
        
        Pagamento existente = pagamento(pagamentoId, ticketId, metodoPagamentoId, new BigDecimal("20.00"), PagamentoStatus.PAGO);
        
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.of(existente));

        PagamentoResponse response = service.pagar(ticketId, new PagarRequest("PIX"));
        
        assertEquals(PagamentoStatus.PAGO, response.status());
        assertEquals(metodoPagamentoId, response.metodoPagamentoId());
        
        verify(pagamentoRepository, never()).save(any());
        verify(registroEventoService, never()).registrarPagamentoConfirmado(any());
    }
    
    @Test
    void deveDarErroDeNotFoundAoTentarPagarSemCalculo() {
        UUID ticketId = UUID.randomUUID();
        
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.empty());

        assertThrows(PagamentoNaoEncontradoException.class, () -> service.pagar(ticketId, new PagarRequest("PIX")));
        verify(pagamentoRepository, never()).save(any());
        verify(registroEventoService, never()).registrarPagamentoConfirmado(any());
    }
    
    @Test
    void deveDarErroAoPagarComMetodoInvalido() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        
        Pagamento existente = pagamento(pagamentoId, ticketId, null, new BigDecimal("20.00"), PagamentoStatus.CALCULADO);
        
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.of(existente));
        when(metodoPagamentoRepository.findByNomeMetodoIgnoreCase("PIX")).thenReturn(Optional.empty());

        assertThrows(MetodoPagamentoInvalidoException.class, () -> service.pagar(ticketId, new PagarRequest("PIX")));
        verify(pagamentoRepository, never()).save(any());
        verify(registroEventoService, never()).registrarPagamentoConfirmado(any());
    }

    @Test
    void deveBuscarPagamentoPorId() {
        UUID pagamentoId = UUID.randomUUID();
        UUID ticketId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();

        Pagamento pagamento = pagamento(pagamentoId, ticketId, metodoPagamentoId, new BigDecimal("20.00"), PagamentoStatus.PAGO);

        when(pagamentoRepository.findById(pagamentoId)).thenReturn(Optional.of(pagamento));

        PagamentoResponse resultado = service.buscarPorId(pagamentoId);

        assertEquals(pagamentoId, resultado.id());
        assertEquals(ticketId, resultado.ticketId());
        assertEquals(metodoPagamentoId, resultado.metodoPagamentoId());
        assertEquals(new BigDecimal("20.00"), resultado.valor());
        assertEquals(PagamentoStatus.PAGO, resultado.status());
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoNaoExistir() {
        UUID pagamentoId = UUID.randomUUID();
        when(pagamentoRepository.findById(pagamentoId)).thenReturn(Optional.empty());

        PagamentoNaoEncontradoException exception = assertThrows(
                PagamentoNaoEncontradoException.class,
                () -> service.buscarPorId(pagamentoId)
        );

        assertEquals("Pagamento não encontrado: " + pagamentoId, exception.getMessage());
    }

    @Test
    void deveBuscarHistoricoDoTicket() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        UUID metodoPagamentoId = UUID.randomUUID();

        Pagamento pagamento = pagamento(pagamentoId, ticketId, metodoPagamentoId, new BigDecimal("20.00"), PagamentoStatus.PAGO);

        when(pagamentoRepository.findByTicketId(ticketId)).thenReturn(Optional.of(pagamento));

        PagamentoResponse resultado = service.buscarPorTicket(ticketId);

        assertEquals(pagamentoId, resultado.id());
        assertEquals(ticketId, resultado.ticketId());
        assertEquals(PagamentoStatus.PAGO, resultado.status());
    }

    private MetodoPagamento metodo(UUID id, String nome) {
        MetodoPagamento metodo = new MetodoPagamento();
        metodo.setId(id);
        metodo.setNomeMetodo(nome);
        return metodo;
    }

    private Pagamento pagamento(UUID id, UUID ticketId, UUID metodoPagamentoId, BigDecimal valor, PagamentoStatus status) {
        Pagamento pagamento = new Pagamento();
        pagamento.setId(id);
        pagamento.setTicketId(ticketId);
        pagamento.setMetodoPagamentoId(metodoPagamentoId);
        pagamento.setValor(valor);
        pagamento.setData(Instant.parse("2026-10-03T12:00:00Z"));
        pagamento.setStatus(status);
        return pagamento;
    }
}
