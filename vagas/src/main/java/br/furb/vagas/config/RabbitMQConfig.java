package br.furb.vagas.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.*;

@Configuration
public class RabbitMQConfig {
    public static final String EXCHANGE = "parking.exchange";
    @Bean
    public Declarables declararTopologia() {
        TopicExchange exchange = new TopicExchange(EXCHANGE, true, false);
        List<Declarable> declaracoes = new ArrayList<>(); declaracoes.add(exchange);
        declararFila(declaracoes, exchange, "vaga.reservar.queue", "vaga.reservar");
        declararFila(declaracoes, exchange, "vaga.liberar.queue", "vaga.liberar");
        return new Declarables(declaracoes);
    }
    private void declararFila(List<Declarable> declaracoes, TopicExchange exchange, String nome, String rota) {
        Queue fila = QueueBuilder.durable(nome).deadLetterExchange("").deadLetterRoutingKey(nome + ".dlq").build();
        declaracoes.add(fila); declaracoes.add(new Queue(nome + ".dlq", true));
        declaracoes.add(BindingBuilder.bind(fila).to(exchange).with(rota));
    }
}
