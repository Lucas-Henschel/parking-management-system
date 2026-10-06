package br.furb.estacionamento;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false", "estacionamento.outbox.habilitada=false"})
@Import(PostgresTestConfiguration.class)
class EstacionamentoApplicationTests {

	@Test
	void contextLoads() {
	}

}
