package br.furb.vagas.service;

import br.furb.vagas.dto.*;
import br.furb.vagas.enums.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

/**
 * Testes contra um PostgreSQL real: exercitam SKIP LOCKED, os CHECK/UNIQUE da migration, a inbox
 * e a outbox transacionais, que não podem ser validados com mocks.
 */
@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false", "vagas.outbox.habilitada=false"})
@Testcontainers
class VagasIntegrationTest {
    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Autowired SetorService setorService;
    @Autowired BlocoService blocoService;
    @Autowired TipoVagaService tipoVagaService;
    @Autowired VagaService vagaService;
    @Autowired ReservaVagaService reservaVagaService;
    @Autowired LiberacaoVagaService liberacaoVagaService;
    @Autowired JdbcTemplate banco;
    @Autowired ObjectMapper conversor;
    @BeforeEach
    void limparRegistros() {
        banco.execute("TRUNCATE evento_pendente, mensagem_processada, reserva_ticket, vaga, bloco, tipo_vaga, setor CASCADE");
    }
    private VagaResponse cadastrarVaga() {
        var setor = setorService.cadastrar(new SetorRequest("A", CadastroStatus.ATIVO));
        var bloco = blocoService.cadastrar(new BlocoRequest(setor.id(), "1", CadastroStatus.ATIVO));
        var tipo = tipoVagaService.cadastrar(new TipoVagaRequest("COMUM"));
        return vagaService.cadastrar(new VagaRequest("A-1", bloco.id(), tipo.id()));
    }
    private MensagemEnvelope<ReservarVagaRequest> mensagem(UUID ticketId) {
        return new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.RESERVAR_VAGA, Instant.now(),
                new ReservarVagaRequest(ticketId));
    }
    private MensagemEnvelope<LiberarVagaRequest> liberacao(UUID ticketId, UUID vagaId) {
        return new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.LIBERAR_VAGA, Instant.now(),
                new LiberarVagaRequest(ticketId, vagaId));
    }
    @Test
    void deveReservarNaTerceiraBuscaEIgnorarReentregaDaSegunda() {
        var vaga = cadastrarVaga();
        UUID ocupante = UUID.randomUUID();
        UUID esperando = UUID.randomUUID();
        reservaVagaService.reservar(mensagem(ocupante));
        var primeira = mensagem(esperando);
        var segunda = mensagem(esperando);
        reservaVagaService.reservar(primeira);
        reservaVagaService.reservar(segunda);
        assertThat(banco.queryForObject("SELECT situacao FROM reserva_ticket WHERE ticket_id = ?",
            String.class, esperando)).isEqualTo("INDISPONIVEL");

        liberacaoVagaService.liberar(liberacao(ocupante, vaga.id()));
        reservaVagaService.reservar(segunda);
        assertThat(vagaService.consultar(vaga.id()).status()).isEqualTo(VagaStatus.LIVRE);
        reservaVagaService.reservar(mensagem(esperando));
        assertThat(vagaService.consultar(vaga.id()).ticketId()).isEqualTo(esperando);
        assertThat(banco.queryForObject("SELECT count(*) FROM evento_pendente WHERE envelope::json->>'correlationId' = ?",
            Integer.class, esperando.toString())).isEqualTo(3);
    }

    @Test
    void devePersistirReservaInboxEOutboxAtomicamente() {
        var vaga = cadastrarVaga(); UUID ticketId = UUID.randomUUID(); var mensagem = mensagem(ticketId);
        reservaVagaService.reservar(mensagem); reservaVagaService.reservar(mensagem);
        assertThat(vagaService.consultar(vaga.id()).ticketId()).isEqualTo(ticketId);
        assertThat(banco.queryForObject("SELECT count(*) FROM evento_pendente", Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("SELECT count(*) FROM mensagem_processada", Integer.class)).isEqualTo(1);
        var resposta = conversor.readTree(banco.queryForObject("SELECT envelope FROM evento_pendente", String.class));
        assertThat(resposta.get("tipo").asString()).isEqualTo("VAGA_RESERVADA");
        assertThat(resposta.get("correlationId").asString()).isEqualTo(ticketId.toString());
        assertThat(resposta.get("payload").get("vagaId").asString()).isEqualTo(vaga.id().toString());
        assertThat(resposta.get("payload").get("numeroVaga").asString()).isEqualTo("A-1");
    }
    @Test
    void deveReverterInboxQuandoLiberacaoForInvalida() {
        var vaga = cadastrarVaga(); UUID ticketId = UUID.randomUUID();
        reservaVagaService.reservar(mensagem(ticketId));
        var invalida = liberacao(ticketId, UUID.randomUUID());
        assertThatThrownBy(() -> liberacaoVagaService.liberar(invalida))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(banco.queryForObject("SELECT count(*) FROM mensagem_processada WHERE id = ?",
                Integer.class, invalida.messageId())).isZero();
        assertThat(vagaService.consultar(vaga.id()).ticketId()).isEqualTo(ticketId);
        liberacaoVagaService.liberar(liberacao(ticketId, vaga.id()));
        assertThat(vagaService.consultar(vaga.id()).status()).isEqualTo(VagaStatus.LIVRE);
    }
    @Test
    void naoDeveGravarInboxParaEnvelopeInvalido() {
        cadastrarVaga(); UUID ticketId = UUID.randomUUID();
        var divergente = new MensagemEnvelope<>(UUID.randomUUID(), UUID.randomUUID(), TipoMensagem.RESERVAR_VAGA,
                Instant.now(), new ReservarVagaRequest(ticketId));
        assertThatThrownBy(() -> reservaVagaService.reservar(divergente)).isInstanceOf(IllegalArgumentException.class);
        assertThat(banco.queryForObject("SELECT count(*) FROM mensagem_processada", Integer.class)).isZero();
        assertThat(banco.queryForObject("SELECT count(*) FROM evento_pendente", Integer.class)).isZero();
    }
    @Test
    void deveReservarUmaUnicaVagaSobConcorrencia() throws Exception {
        cadastrarVaga();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> tarefas = new ArrayList<>();
            for (int indice = 0; indice < 8; indice++) tarefas.add(() -> {
                UUID ticketId = UUID.randomUUID(); reservaVagaService.reservar(mensagem(ticketId)); return null;
            });
            for (var resultado : executor.invokeAll(tarefas)) resultado.get(15, TimeUnit.SECONDS);
        }
        assertThat(banco.queryForObject("SELECT count(*) FROM vaga WHERE status = 'OCUPADA'", Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("SELECT count(*) FROM reserva_ticket WHERE situacao = 'RESERVADA'",
                Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("SELECT count(*) FROM evento_pendente", Integer.class)).isEqualTo(8);
    }
    @Test
    void deveSerializarDiferentesMensagensDoMesmoTicket() throws Exception {
        var vaga = cadastrarVaga(); UUID ticketId = UUID.randomUUID();
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Callable<Void>> tarefas = new ArrayList<>();
            for (int indice = 0; indice < 4; indice++) tarefas.add(() -> {
                reservaVagaService.reservar(mensagem(ticketId)); return null;
            });
            for (var resultado : executor.invokeAll(tarefas)) resultado.get(15, TimeUnit.SECONDS);
        }
        assertThat(vagaService.consultar(vaga.id()).ticketId()).isEqualTo(ticketId);
        assertThat(banco.queryForObject("SELECT count(*) FROM reserva_ticket", Integer.class)).isEqualTo(1);
    }
    @Test
    void deveIgnorarSetorInativoNaSelecao() {
        var vaga = cadastrarVaga(); var bloco = blocoService.consultar(vaga.blocoId());
        banco.update("UPDATE setor SET status = 'INATIVO' WHERE id = ?", bloco.setorId());
        UUID ticketId = UUID.randomUUID(); reservaVagaService.reservar(mensagem(ticketId));
        assertThat(vagaService.consultar(vaga.id()).status()).isEqualTo(VagaStatus.LIVRE);
        assertThat(banco.queryForObject("SELECT count(*) FROM reserva_ticket WHERE situacao = 'INDISPONIVEL'",
                Integer.class)).isEqualTo(1);
    }
}
