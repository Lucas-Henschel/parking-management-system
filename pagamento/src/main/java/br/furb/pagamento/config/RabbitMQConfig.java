package br.furb.pagamento.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "parking.exchange";

    public static final String PAGAMENTO_QUEUE = "pagamento.calcular.queue";
    public static final String PAGAMENTO_DLQ = "pagamento.calcular.queue.dlq";

    public static final String CALCULAR_PAGAMENTO_ROUTING_KEY = "pagamento.calcular";
    public static final String PAGAMENTO_CALCULADO_ROUTING_KEY = "pagamento.calculado";
    public static final String PAGAMENTO_CONFIRMADO_ROUTING_KEY = "pagamento.confirmado";
    public static final String PAGAMENTO_DLQ_ROUTING_KEY = "pagamento.calcular.dlq";

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
        return new JacksonJsonMessageConverter(JsonMapper.builder().build());
    }

    /**
     * Erros permanentes (dados inválidos, mensagem malformada) não são retentados e vão
     * direto para a DLQ. Os demais seguem a política de retry de application.properties.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer retrySettingsCustomizer() {
        return settings -> settings.setExceptionPredicate(throwable -> {
            for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
                if (t instanceof IllegalArgumentException || t instanceof MessageConversionException) {
                    return false;
                }
            }

            return true;
        });
    }
}
