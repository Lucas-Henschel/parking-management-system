package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PagamentoPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    private PagamentoPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PagamentoPublisher(rabbitTemplate);
    }

    @Test
    void devePublicarPagamentoCalculadoNoRabbitMQ() {
        PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
                10L,
                1L,
                new BigDecimal("20.00"),
                LocalDateTime.of(2026, 10, 3, 12, 0),
                "PAGO"
        );

        publisher.publicarPagamentoCalculado(evento);

        verify(rabbitTemplate).convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAGAMENTO_CALCULADO_ROUTING_KEY,
                evento
        );
    }
}
