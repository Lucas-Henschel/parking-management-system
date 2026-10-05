package br.furb.pagamento.messaging;

import br.furb.pagamento.config.RabbitMQConfig;
import br.furb.pagamento.dto.MensagemEnvelope;
import br.furb.pagamento.dto.PagamentoCalculadoEvent;
import br.furb.pagamento.dto.PagamentoConfirmadoEvent;
import br.furb.pagamento.enums.PagamentoStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
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
        UUID ticketId = UUID.randomUUID();
        PagamentoCalculadoEvent evento = new PagamentoCalculadoEvent(
                UUID.randomUUID(),
                ticketId,
                new BigDecimal("20.00"),
                Instant.parse("2026-10-03T12:00:00Z"),
                PagamentoStatus.CALCULADO
        );

        publisher.publicarPagamentoCalculado(evento);

        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.PAGAMENTO_CALCULADO_ROUTING_KEY),
                captor.capture()
        );
        
        MensagemEnvelope envelope = captor.getValue();
        assertNotNull(envelope.messageId());
        assertEquals(ticketId, envelope.correlationId());
        assertEquals("PAGAMENTO_CALCULADO", envelope.tipo());
        assertEquals(evento, envelope.payload());
    }
    
    @Test
    void devePublicarPagamentoConfirmadoNoRabbitMQ() {
        UUID ticketId = UUID.randomUUID();
        PagamentoConfirmadoEvent evento = new PagamentoConfirmadoEvent(
                UUID.randomUUID(),
                ticketId,
                new BigDecimal("20.00"),
                "PIX",
                PagamentoStatus.PAGO
        );

        publisher.publicarPagamentoConfirmado(evento);

        ArgumentCaptor<MensagemEnvelope> captor = ArgumentCaptor.forClass(MensagemEnvelope.class);
        
        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.PAGAMENTO_CONFIRMADO_ROUTING_KEY),
                captor.capture()
        );
        
        MensagemEnvelope envelope = captor.getValue();
        assertNotNull(envelope.messageId());
        assertEquals(ticketId, envelope.correlationId());
        assertEquals("PAGAMENTO_CONFIRMADO", envelope.tipo());
        assertEquals(evento, envelope.payload());
    }
}
