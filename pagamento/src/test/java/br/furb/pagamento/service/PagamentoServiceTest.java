package br.furb.pagamento.service;

import br.furb.pagamento.dto.*;
import br.furb.pagamento.entity.MetodoPagamento;
import br.furb.pagamento.entity.Pagamento;
import br.furb.pagamento.entity.PagamentoStatus;
import br.furb.pagamento.exception.PagamentoNaoEncontradoException;
import br.furb.pagamento.messaging.PagamentoPublisher;
import br.furb.pagamento.repository.MetodoPagamentoRepository;
import br.furb.pagamento.repository.PagamentoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
    void deveCalcularPagamentoDeUmaHora() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();

        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime saida = entrada.plusMinutes(30);

        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, saida);

        Pagamento salvo = pagamento(pagamentoId, ticketId, null, new BigDecimal("10.00"), PagamentoStatus.CALCULADO);

        when(pagamentoRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
        when(pagamentoRepository.save(any(Pagamento.class))).thenReturn(salvo);

        PagamentoCalculadoEvent resultado = service.calcularPagamento(request);

        assertEquals(pagamentoId, resultado.pagamentoId());
        assertEquals(ticketId, resultado.ticketId());
        assertEquals(new BigDecimal("10.00"), resultado.valor());
        assertEquals(PagamentoStatus.CALCULADO, resultado.status());

        verify(pagamentoRepository).save(any(Pagamento.class));
        verify(pagamentoPublisher).publicarPagamentoCalculado(resultado);
    }

    @Test
    void deveCobrarDuasHorasQuandoUltrapassarUmaHora() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();

        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime saida = entrada.plusMinutes(61);

        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, saida);

        when(pagamentoRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());
        when(pagamentoRepository.save(any(Pagamento.class)))
                .thenReturn(pagamento(pagamentoId, ticketId, null, new BigDecimal("20.00"), PagamentoStatus.CALCULADO));

        PagamentoCalculadoEvent resultado = service.calcularPagamento(request);

        assertEquals(new BigDecimal("20.00"), resultado.valor());
    }

    @Test
    void deveRejeitarSaidaAnteriorOuIgualAEntrada() {
        UUID ticketId = UUID.randomUUID();

        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, entrada);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> service.calcularPagamento(request)
        );

        assertEquals("A saída deve ser posterior à entrada", exception.getMessage());
        verifyNoInteractions(metodoPagamentoRepository, pagamentoRepository, pagamentoPublisher);
    }

    @Test
    void deveReutilizarPagamentoQuandoTicketJaFoiProcessado() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();

        Pagamento existente = pagamento(pagamentoId, ticketId, null, new BigDecimal("20.00"), PagamentoStatus.CALCULADO);

        CalcularPagamentoRequest request = new CalcularPagamentoRequest(
                ticketId,
                LocalDateTime.of(2026, 10, 3, 10, 0),
                LocalDateTime.of(2026, 10, 3, 11, 30)
        );

        when(pagamentoRepository.findByTicketId(ticketId)).thenReturn(Optional.of(existente));

        PagamentoCalculadoEvent resultado = service.calcularPagamento(request);

        assertEquals(pagamentoId, resultado.pagamentoId());
        assertEquals(ticketId, resultado.ticketId());
        assertEquals(new BigDecimal("20.00"), resultado.valor());

        verify(pagamentoRepository, never()).save(any());
        verify(pagamentoPublisher).publicarPagamentoCalculado(resultado);
    }

    @Test
    void deveSalvarDadosCorretosNoPagamento() {
        UUID ticketId = UUID.randomUUID();

        LocalDateTime entrada = LocalDateTime.of(2026, 10, 3, 10, 0);
        LocalDateTime saida = entrada.plusHours(2);

        CalcularPagamentoRequest request = new CalcularPagamentoRequest(ticketId, entrada, saida);

        when(pagamentoRepository.findByTicketId(ticketId)).thenReturn(Optional.empty());

        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(invocation -> {
            Pagamento pagamento = invocation.getArgument(0);
            pagamento.setId(UUID.randomUUID());
            pagamento.setData(LocalDateTime.now());
            return pagamento;
        });

        service.calcularPagamento(request);

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepository).save(captor.capture());

        Pagamento salvo = captor.getValue();
        assertEquals(ticketId, salvo.getTicketId());
        assertNull(salvo.getMetodoPagamentoId());
        assertEquals(new BigDecimal("20.00"), salvo.getValor());
        assertEquals(PagamentoStatus.CALCULADO, salvo.getStatus());
        assertNotNull(salvo.getData());
        assertNotNull(salvo.getId());
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
        
        verify(pagamentoPublisher).publicarPagamentoConfirmado(any());
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
        verify(pagamentoPublisher, never()).publicarPagamentoConfirmado(any());
    }
    
    @Test
    void deveDarErroDeNotFoundAoTentarPagarSemCalculo() {
        UUID ticketId = UUID.randomUUID();
        
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.empty());

        assertThrows(PagamentoNaoEncontradoException.class, () -> service.pagar(ticketId, new PagarRequest("PIX")));
        verify(pagamentoRepository, never()).save(any());
        verify(pagamentoPublisher, never()).publicarPagamentoConfirmado(any());
    }
    
    @Test
    void deveDarErroAoPagarComMetodoInvalido() {
        UUID ticketId = UUID.randomUUID();
        UUID pagamentoId = UUID.randomUUID();
        
        Pagamento existente = pagamento(pagamentoId, ticketId, null, new BigDecimal("20.00"), PagamentoStatus.CALCULADO);
        
        when(pagamentoRepository.findByTicketIdForUpdate(ticketId)).thenReturn(Optional.of(existente));
        when(metodoPagamentoRepository.findByNomeMetodoIgnoreCase("PIX")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.pagar(ticketId, new PagarRequest("PIX")));
        verify(pagamentoRepository, never()).save(any());
        verify(pagamentoPublisher, never()).publicarPagamentoConfirmado(any());
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
        pagamento.setData(LocalDateTime.of(2026, 10, 3, 12, 0));
        pagamento.setStatus(status);
        return pagamento;
    }
}
