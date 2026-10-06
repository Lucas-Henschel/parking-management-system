package br.furb.estacionamento.config;

import br.furb.estacionamento.exception.ConflitoNegocioException;
import br.furb.estacionamento.exception.RecursoNaoEncontradoException;

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

    public static final String VAGA_RESULTADO_QUEUE = "estacionamento.vaga-resultado.queue";
    public static final String VAGA_RESULTADO_DLQ = "estacionamento.vaga-resultado.queue.dlq";
    public static final String PAGAMENTO_CALCULADO_QUEUE = "estacionamento.pagamento-calculado.queue";
    public static final String PAGAMENTO_CALCULADO_DLQ = "estacionamento.pagamento-calculado.queue.dlq";
    public static final String PAGAMENTO_CONFIRMADO_QUEUE = "estacionamento.pagamento-confirmado.queue";
    public static final String PAGAMENTO_CONFIRMADO_DLQ = "estacionamento.pagamento-confirmado.queue.dlq";

    public static final String VAGA_RESERVAR_ROUTING_KEY = "vaga.reservar";
    public static final String VAGA_LIBERAR_ROUTING_KEY = "vaga.liberar";
    public static final String VAGA_RESERVADA_ROUTING_KEY = "vaga.reservada";
    public static final String VAGA_INDISPONIVEL_ROUTING_KEY = "vaga.indisponivel";
    public static final String PAGAMENTO_CALCULAR_ROUTING_KEY = "pagamento.calcular";
    public static final String PAGAMENTO_CALCULADO_ROUTING_KEY = "pagamento.calculado";
    public static final String PAGAMENTO_CONFIRMADO_ROUTING_KEY = "pagamento.confirmado";

    @Bean
    public TopicExchange parkingExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue vagaResultadoQueue() {
        return filaComDlq(VAGA_RESULTADO_QUEUE, VAGA_RESULTADO_DLQ);
    }

    @Bean
    public Queue vagaResultadoDlq() {
        return QueueBuilder.durable(VAGA_RESULTADO_DLQ).build();
    }

    @Bean
    public Queue pagamentoCalculadoQueue() {
        return filaComDlq(PAGAMENTO_CALCULADO_QUEUE, PAGAMENTO_CALCULADO_DLQ);
    }

    @Bean
    public Queue pagamentoCalculadoDlq() {
        return QueueBuilder.durable(PAGAMENTO_CALCULADO_DLQ).build();
    }

    @Bean
    public Queue pagamentoConfirmadoQueue() {
        return filaComDlq(PAGAMENTO_CONFIRMADO_QUEUE, PAGAMENTO_CONFIRMADO_DLQ);
    }

    @Bean
    public Queue pagamentoConfirmadoDlq() {
        return QueueBuilder.durable(PAGAMENTO_CONFIRMADO_DLQ).build();
    }

    @Bean
    public Binding vagaReservadaBinding(Queue vagaResultadoQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaResultadoQueue).to(parkingExchange).with(VAGA_RESERVADA_ROUTING_KEY);
    }

    @Bean
    public Binding vagaIndisponivelBinding(Queue vagaResultadoQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaResultadoQueue).to(parkingExchange).with(VAGA_INDISPONIVEL_ROUTING_KEY);
    }

    @Bean
    public Binding vagaResultadoDlqBinding(Queue vagaResultadoDlq, TopicExchange parkingExchange) {
        return BindingBuilder.bind(vagaResultadoDlq).to(parkingExchange).with(VAGA_RESULTADO_DLQ);
    }

    @Bean
    public Binding pagamentoCalculadoBinding(Queue pagamentoCalculadoQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(pagamentoCalculadoQueue).to(parkingExchange).with(PAGAMENTO_CALCULADO_ROUTING_KEY);
    }

    @Bean
    public Binding pagamentoCalculadoDlqBinding(Queue pagamentoCalculadoDlq, TopicExchange parkingExchange) {
        return BindingBuilder.bind(pagamentoCalculadoDlq).to(parkingExchange).with(PAGAMENTO_CALCULADO_DLQ);
    }

    @Bean
    public Binding pagamentoConfirmadoBinding(Queue pagamentoConfirmadoQueue, TopicExchange parkingExchange) {
        return BindingBuilder.bind(pagamentoConfirmadoQueue).to(parkingExchange).with(PAGAMENTO_CONFIRMADO_ROUTING_KEY);
    }

    @Bean
    public Binding pagamentoConfirmadoDlqBinding(Queue pagamentoConfirmadoDlq, TopicExchange parkingExchange) {
        return BindingBuilder.bind(pagamentoConfirmadoDlq).to(parkingExchange).with(PAGAMENTO_CONFIRMADO_DLQ);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new JacksonJsonMessageConverter(JsonMapper.builder().build());
    }

    /**
     * Erros permanentes (dados inválidos, conflito de negócio, mensagem malformada) não são
     * retentados e vão direto para a DLQ. Os demais seguem a política de retry de application.properties.
     */
    @Bean
    public RabbitListenerRetrySettingsCustomizer retrySettingsCustomizer() {
        return settings -> settings.setExceptionPredicate(throwable -> {
            for (Throwable t = throwable; t != null; t = t.getCause() == t ? null : t.getCause()) {
                if (t instanceof IllegalArgumentException
                    || t instanceof MessageConversionException
                    || t instanceof ConflitoNegocioException
                    || t instanceof RecursoNaoEncontradoException) {
                    return false;
                }
            }

            return true;
        });
    }

    private Queue filaComDlq(String nome, String dlqRoutingKey) {
        return QueueBuilder.durable(nome)
            .deadLetterExchange(EXCHANGE)
            .deadLetterRoutingKey(dlqRoutingKey)
            .build();
    }
}
