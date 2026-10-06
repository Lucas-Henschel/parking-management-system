package br.furb.vagas.service;

import br.furb.vagas.entity.EventoPendente;
import br.furb.vagas.enums.ResultadoPublicacao;
import br.furb.vagas.messaging.VagaPublisher;
import br.furb.vagas.repository.EventoPendenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Exercita a outbox contra um PostgreSQL real: a query com SKIP LOCKED, a coluna de backoff e a
 * gravação da falha na mesma transação.
 */
@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false", "vagas.outbox.habilitada=false"})
@Testcontainers
class OutboxIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired PublicacaoOutboxService publicacao;
    @Autowired EventoPendenteRepository eventos;
    @Autowired JdbcTemplate banco;
    @Autowired TransactionTemplate transacao;
    @MockitoBean VagaPublisher publicador;

    @BeforeEach
    void limpar() {
        banco.execute("TRUNCATE evento_pendente");
    }

    private UUID registrar(String rota, Instant criadoEm) {
        UUID id = UUID.randomUUID();
        transacao.executeWithoutResult(status -> eventos.registrar(id, rota, "{}", criadoEm));
        return id;
    }

    private boolean publicado(UUID id) {
        return banco.queryForObject("SELECT publicado_em IS NOT NULL FROM evento_pendente WHERE id = ?",
                Boolean.class, id);
    }

    @Test
    void eventoQueFalhaNaoBloqueiaOsSeguintes() {
        Instant agora = Instant.now();
        UUID primeiro = registrar("vaga.reservada", agora.minus(2, ChronoUnit.SECONDS));
        UUID segundo = registrar("vaga.indisponivel", agora.minus(1, ChronoUnit.SECONDS));
        doThrow(new IllegalStateException("NO_ROUTE")).when(publicador)
                .publicar(argThat(evento -> evento.obterId().equals(primeiro)));

        assertThat(publicacao.publicarProximo()).isEqualTo(ResultadoPublicacao.FALHOU);
        assertThat(publicacao.publicarProximo()).isEqualTo(ResultadoPublicacao.PUBLICADO);
        assertThat(publicacao.publicarProximo()).isEqualTo(ResultadoPublicacao.SEM_EVENTO);

        assertThat(publicado(primeiro)).isFalse();
        assertThat(publicado(segundo)).isTrue();
        assertThat(banco.queryForObject("SELECT tentativas FROM evento_pendente WHERE id = ?",
                Integer.class, primeiro)).isEqualTo(1);
        assertThat(banco.queryForObject("SELECT ultimo_erro FROM evento_pendente WHERE id = ?",
                String.class, primeiro)).contains("NO_ROUTE");
        assertThat(banco.queryForObject("SELECT proxima_tentativa_em > now() FROM evento_pendente WHERE id = ?",
                Boolean.class, primeiro)).isTrue();
    }

    @Test
    void eventoEmBackoffNaoEhTentadoAntesDoPrazo() {
        UUID id = registrar("vaga.reservada", Instant.now().minus(1, ChronoUnit.SECONDS));
        banco.update("UPDATE evento_pendente SET proxima_tentativa_em = now() + interval '1 hour' WHERE id = ?", id);

        assertThat(publicacao.publicarProximo()).isEqualTo(ResultadoPublicacao.SEM_EVENTO);

        verify(publicador, never()).publicar(argThat(evento -> true));
    }

    @Test
    void eventoVoltaASerPublicadoQuandoOBackoffVence() {
        UUID id = registrar("vaga.reservada", Instant.now().minus(1, ChronoUnit.SECONDS));
        banco.update("UPDATE evento_pendente SET tentativas = 2, proxima_tentativa_em = now() - interval '1 second' "
                + "WHERE id = ?", id);

        assertThat(publicacao.publicarProximo()).isEqualTo(ResultadoPublicacao.PUBLICADO);

        assertThat(publicado(id)).isTrue();
    }

    @Test
    void deveGravarOEventoComAProximaTentativaImediata() {
        Instant criadoEm = Instant.now().minus(1, ChronoUnit.SECONDS);
        UUID id = registrar("vaga.reservada", criadoEm);

        EventoPendente evento = eventos.findById(id).orElseThrow();

        assertThat(evento.obterTentativas()).isZero();
        assertThat(evento.obterProximaTentativaEm()).isCloseTo(criadoEm, org.assertj.core.api.Assertions.within(1, ChronoUnit.MILLIS));
        assertThat(evento.obterPublicadoEm()).isNull();
    }
}
