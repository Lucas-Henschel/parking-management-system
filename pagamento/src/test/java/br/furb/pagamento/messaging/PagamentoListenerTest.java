package br.furb.pagamento.messaging;

import br.furb.pagamento.dto.CalcularPagamentoRequest;
import br.furb.pagamento.service.PagamentoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PagamentoListenerTest {

    @Mock
    private PagamentoService pagamentoService;

    private PagamentoListener listener;

    @BeforeEach
    void setUp() {
        listener = new PagamentoListener(pagamentoService);
    }

    @Test
    void deveEncaminharMensagemParaOServicoDePagamento() {
        CalcularPagamentoRequest request = new CalcularPagamentoRequest(
                1L,
                LocalDateTime.of(2026, 10, 3, 10, 0),
                LocalDateTime.of(2026, 10, 3, 11, 0),
                2L
        );

        listener.receberCalculo(request);

        verify(pagamentoService).calcularPagamento(request);
    }
}
