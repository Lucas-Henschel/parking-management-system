package br.furb.pagamento.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "parking.exchange";
    public static final String PAGAMENTO_QUEUE = "pagamento.queue";
    public static final String PAGAMENTO_DLQ = "pagamento.dlq";

    public static final String CALCULAR_PAGAMENTO_ROUTING_KEY = "calcular.pagamento";
    public static final String PAGAMENTO_CALCULADO_ROUTING_KEY = "pagamento.calculado";
    public static final String PAGAMENTO_DLQ_ROUTING_KEY = "pagamento.dlq";

    @Bean
    public TopicExchange parkingExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue pagamentoQueue() {
        return QueueBuilder.durable(PAGAMENTO_QUEUE)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(PAGAMENTO_DLQ_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue pagamentoDlq() {
        return QueueBuilder.durable(PAGAMENTO_DLQ).build();
    }

    @Bean
    public Binding pagamentoBinding(Queue pagamentoQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(pagamentoQueue)
                .to(parkingExchange)
                .with(CALCULAR_PAGAMENTO_ROUTING_KEY);
    }

    @Bean
    public Binding pagamentoDlqBinding(Queue pagamentoDlq, TopicExchange parkingExchange) {
        return BindingBuilder.bind(pagamentoDlq)
                .to(parkingExchange)
                .with(PAGAMENTO_DLQ_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
