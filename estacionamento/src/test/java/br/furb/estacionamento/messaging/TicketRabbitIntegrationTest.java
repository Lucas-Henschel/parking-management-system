package br.furb.estacionamento.messaging;

import br.furb.estacionamento.config.RabbitMQConfig;
import br.furb.estacionamento.entity.EventoPendente;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class TicketRabbitIntegrationTest {
    private static final GenericContainer<?> broker = new GenericContainer<>(DockerImageName.parse("rabbitmq:4-management-alpine"))
            .withEnv("RABBITMQ_DEFAULT_USER", "parking").withEnv("RABBITMQ_DEFAULT_PASS", "parking")
            .withExposedPorts(5672);
    private static CachingConnectionFactory conexao;
    private static RabbitTemplate rabbit;
    private static TicketOutboxPublisher publisher;

    @BeforeAll
    static void iniciar() {
        broker.start();
        conexao = new CachingConnectionFactory(broker.getHost(), broker.getMappedPort(5672));
        conexao.setUsername("parking");
        conexao.setPassword("parking");
        conexao.setPublisherConfirmType(CachingConnectionFactory.ConfirmType.CORRELATED);
        conexao.setPublisherReturns(true);
        rabbit = new RabbitTemplate(conexao);
        rabbit.setMandatory(true);
        publisher = new TicketOutboxPublisher(rabbit);
        new RabbitAdmin(conexao).declareExchange(new TopicExchange(RabbitMQConfig.EXCHANGE, true, false));
    }

    @AfterAll
    static void encerrar() {
        if (conexao != null) conexao.destroy();
        broker.close();
    }

    @Test
    void brokerConfirmaERoteiaMensagemPersistenteComMesmoId() {
        var admin = new RabbitAdmin(conexao);
        var fila = new Queue("teste." + UUID.randomUUID(), true);
        admin.declareQueue(fila);
        admin.declareBinding(BindingBuilder.bind(fila).to(new TopicExchange(RabbitMQConfig.EXCHANGE)).with("teste.reserva"));
        var evento = new EventoPendente(UUID.randomUUID(), "teste.reserva", "{\"tipo\":\"RESERVAR_VAGA\"}", java.time.Instant.now());
        publisher.publicar(evento);
        var recebido = rabbit.receive(fila.getName(), 5000);
        assertNotNull(recebido);
        assertEquals(evento.obterEnvelope(), new String(recebido.getBody(), StandardCharsets.UTF_8));
        assertEquals(evento.obterId().toString(), recebido.getMessageProperties().getMessageId());
        assertEquals(MessageDeliveryMode.PERSISTENT, recebido.getMessageProperties().getReceivedDeliveryMode());
    }

    @Test
    void brokerDevolveMensagemQuandoConsumidorAindaNaoCriouFila() {
        var evento = new EventoPendente(UUID.randomUUID(), "sem.rota." + UUID.randomUUID(), "{}", java.time.Instant.now());
        assertThrows(IllegalStateException.class, () -> publisher.publicar(evento));
    }
}
