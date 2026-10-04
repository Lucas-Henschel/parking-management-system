package br.furb.pagamento.messaging;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.repository.MensagemProcessadaRepository;
import br.furb.pagamento.service.PagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PagamentoListenerTest {

    @Mock
    private PagamentoService pagamentoService;
    
    @Mock
    private MensagemProcessadaRepository mensagemProcessadaRepository;

    private PagamentoListener listener;

    @BeforeEach
    void setUp() {
        listener = new PagamentoListener(pagamentoService, mensagemProcessadaRepository);
    }

    @Test
    void deveEncaminharMensagemParaOServicoDePagamento() {
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 3, 10, 0),
                LocalDateTime.of(2026, 10, 3, 11, 0)
        );
        
        UUID messageId = UUID.randomUUID();
        
        MensagemEnvelope<CalcularPagamentoRequest> envelope = new MensagemEnvelope<>(
                messageId, request.ticketId(), "CALCULAR_PAGAMENTO", LocalDateTime.now(), request
        );

        when(mensagemProcessadaRepository.existsById(messageId)).thenReturn(false);

        listener.receberCalculo(envelope);

        verify(pagamentoService).calcularPagamento(request);
        verify(mensagemProcessadaRepository).save(any());
    }
    
    @Test
    void naoDeveProcessarMensagemRepetida() {
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(
                UUID.randomUUID(),
                LocalDateTime.of(2026, 10, 3, 10, 0),
                LocalDateTime.of(2026, 10, 3, 11, 0)
        );
        
        UUID messageId = UUID.randomUUID();
        
        MensagemEnvelope<CalcularPagamentoRequest> envelope = new MensagemEnvelope<>(
                messageId, request.ticketId(), "CALCULAR_PAGAMENTO", LocalDateTime.now(), request
        );

        when(mensagemProcessadaRepository.existsById(messageId)).thenReturn(true);

        listener.receberCalculo(envelope);

        verify(pagamentoService, never()).calcularPagamento(any());
        verify(mensagemProcessadaRepository, never()).save(any());
    }
}
