package br.furb.estacionamento.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
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
import org.springframework.web.server.ResponseStatusException;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "parking.exchange";

    public static final String VAGA_RESULTADO_QUEUE = "estacionamento.vaga-resultado.queue";
    public static final String PAGAMENTO_CALCULADO_QUEUE = "estacionamento.pagamento-calculado.queue";
    public static final String PAGAMENTO_CONFIRMADO_QUEUE = "estacionamento.pagamento-confirmado.queue";

    public static final String VAGA_RESERVAR_ROUTING_KEY = "vaga.reservar";
    public static final String VAGA_LIBERAR_ROUTING_KEY = "vaga.liberar";
    public static final String VAGA_RESERVADA_ROUTING_KEY = "vaga.reservada";
    public static final String VAGA_INDISPONIVEL_ROUTING_KEY = "vaga.indisponivel";
    public static final String PAGAMENTO_CALCULAR_ROUTING_KEY = "pagamento.calcular";
    public static final String PAGAMENTO_CALCULADO_ROUTING_KEY = "pagamento.calculado";
    public static final String PAGAMENTO_CONFIRMADO_ROUTING_KEY = "pagamento.confirmado";

    private static final String VAGA_RESULTADO_DLQ = VAGA_RESULTADO_QUEUE + ".dlq";
    private static final String PAGAMENTO_CALCULADO_DLQ = PAGAMENTO_CALCULADO_QUEUE + ".dlq";
    private static final String PAGAMENTO_CONFIRMADO_DLQ = PAGAMENTO_CONFIRMADO_QUEUE + ".dlq";

    @Bean
    public TopicExchange parkingExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Declarables estacionamentoQueuesAndBindings(TopicExchange parkingExchange) {
        Queue vagaResultado = durableQueue(VAGA_RESULTADO_QUEUE, VAGA_RESULTADO_DLQ);
        Queue vagaResultadoDlq = QueueBuilder.durable(VAGA_RESULTADO_DLQ).build();
        Queue pagamentoCalculado = durableQueue(PAGAMENTO_CALCULADO_QUEUE, PAGAMENTO_CALCULADO_DLQ);
        Queue pagamentoCalculadoDlq = QueueBuilder.durable(PAGAMENTO_CALCULADO_DLQ).build();
        Queue pagamentoConfirmado = durableQueue(PAGAMENTO_CONFIRMADO_QUEUE, PAGAMENTO_CONFIRMADO_DLQ);
        Queue pagamentoConfirmadoDlq = QueueBuilder.durable(PAGAMENTO_CONFIRMADO_DLQ).build();

        return new Declarables(
                vagaResultado,
                vagaResultadoDlq,
                pagamentoCalculado,
                pagamentoCalculadoDlq,
                pagamentoConfirmado,
                pagamentoConfirmadoDlq,
                BindingBuilder.bind(vagaResultado).to(parkingExchange).with(VAGA_RESERVADA_ROUTING_KEY),
                BindingBuilder.bind(vagaResultado).to(parkingExchange).with(VAGA_INDISPONIVEL_ROUTING_KEY),
                BindingBuilder.bind(vagaResultadoDlq).to(parkingExchange).with(VAGA_RESULTADO_DLQ),
                BindingBuilder.bind(pagamentoCalculado).to(parkingExchange).with(PAGAMENTO_CALCULADO_ROUTING_KEY),
                BindingBuilder.bind(pagamentoCalculadoDlq).to(parkingExchange).with(PAGAMENTO_CALCULADO_DLQ),
                BindingBuilder.bind(pagamentoConfirmado).to(parkingExchange).with(PAGAMENTO_CONFIRMADO_ROUTING_KEY),
                BindingBuilder.bind(pagamentoConfirmadoDlq).to(parkingExchange).with(PAGAMENTO_CONFIRMADO_DLQ)
        );
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter(JsonMapper.builder().build());
    }

    @Bean
    public RabbitListenerRetrySettingsCustomizer retrySettingsCustomizer() {
        return settings -> settings.setExceptionPredicate(throwable -> {
            for (Throwable cause = throwable; cause != null; cause = cause.getCause() == cause ? null : cause.getCause()) {
                if (cause instanceof IllegalArgumentException || cause instanceof MessageConversionException
                        || cause instanceof ResponseStatusException status && status.getStatusCode().is4xxClientError()) {
                    return false;
                }
            }
            return true;
        });
    }

    private Queue durableQueue(String queueName, String deadLetterRoutingKey) {
        return QueueBuilder.durable(queueName)
                .deadLetterExchange(EXCHANGE)
                .deadLetterRoutingKey(deadLetterRoutingKey)
                .build();
    }
}
