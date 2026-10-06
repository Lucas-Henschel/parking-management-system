package br.furb.vagas.config;

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

    public static final String VAGA_RESERVAR_QUEUE = "vaga.reservar.queue";
    public static final String VAGA_RESERVAR_DLQ = "vaga.reservar.queue.dlq";
    public static final String VAGA_LIBERAR_QUEUE = "vaga.liberar.queue";
    public static final String VAGA_LIBERAR_DLQ = "vaga.liberar.queue.dlq";

    public static final String RESERVAR_VAGA_ROUTING_KEY = "vaga.reservar";
    public static final String LIBERAR_VAGA_ROUTING_KEY = "vaga.liberar";
    public static final String VAGA_RESERVADA_ROUTING_KEY = "vaga.reservada";
    public static final String VAGA_INDISPONIVEL_ROUTING_KEY = "vaga.indisponivel";
    public static final String VAGA_RESERVAR_DLQ_ROUTING_KEY = "vaga.reservar.dlq";
    public static final String VAGA_LIBERAR_DLQ_ROUTING_KEY = "vaga.liberar.dlq";

    @Bean
    public TopicExchange parkingExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue vagaReservarQueue() {
        return QueueBuilder.durable(VAGA_RESERVAR_QUEUE)
            .deadLetterExchange(EXCHANGE)
            .deadLetterRoutingKey(VAGA_RESERVAR_DLQ_ROUTING_KEY)
            .build();
    }

    @Bean
    public Queue vagaReservarDlq() {
        return QueueBuilder.durable(VAGA_RESERVAR_DLQ).build();
    }

    @Bean
    public Queue vagaLiberarQueue() {
        return QueueBuilder.durable(VAGA_LIBERAR_QUEUE)
            .deadLetterExchange(EXCHANGE)
            .deadLetterRoutingKey(VAGA_LIBERAR_DLQ_ROUTING_KEY)
            .build();
    }

    @Bean
    public Queue vagaLiberarDlq() {
        return QueueBuilder.durable(VAGA_LIBERAR_DLQ).build();
    }

    @Bean
    public Binding vagaReservarBinding(Queue vagaReservarQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaReservarQueue).to(parkingExchange).with(RESERVAR_VAGA_ROUTING_KEY);
    }

    @Bean
    public Binding vagaReservarDlqBinding(Queue vagaReservarDlq, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaReservarDlq).to(parkingExchange).with(VAGA_RESERVAR_DLQ_ROUTING_KEY);
    }

    @Bean
    public Binding vagaLiberarBinding(Queue vagaLiberarQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaLiberarQueue).to(parkingExchange).with(LIBERAR_VAGA_ROUTING_KEY);
    }

    @Bean
    public Binding vagaLiberarDlqBinding(Queue vagaLiberarDlq, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaLiberarDlq).to(parkingExchange).with(VAGA_LIBERAR_DLQ_ROUTING_KEY);
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
