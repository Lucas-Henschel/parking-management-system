package br.furb.vagas.service;

import br.furb.vagas.dto.*;
import br.furb.vagas.service.*;
import br.furb.vagas.entity.*;
import br.furb.vagas.messaging.*;
import br.furb.vagas.dto.MensagemEnvelope;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false", "vagas.outbox.habilitada=false"})
@EnabledIfEnvironmentVariable(named = "VAGAS_TEST_DB_URL", matches = ".+")
class VagasIntegrationTest {
    private static final String ESQUEMA = "teste_vagas_" + UUID.randomUUID().toString().replace("-", "");
    @DynamicPropertySource
    static void configurarBanco(DynamicPropertyRegistry propriedades) {
        propriedades.add("spring.datasource.url", () -> System.getenv("VAGAS_TEST_DB_URL"));
        propriedades.add("spring.datasource.username", () -> usuario());
        propriedades.add("spring.datasource.password", () -> senha());
        propriedades.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + ESQUEMA);
        propriedades.add("spring.flyway.schemas", () -> ESQUEMA);
        propriedades.add("spring.flyway.default-schema", () -> ESQUEMA);
        propriedades.add("spring.jpa.properties.hibernate.default_schema", () -> ESQUEMA);
    }
    private static String usuario() { return System.getenv().getOrDefault("VAGAS_TEST_DB_USER", "parking"); }
    private static String senha() { return System.getenv().getOrDefault("VAGAS_TEST_DB_PASSWORD", "parking"); }
    @AfterAll
    static void removerEsquemaDeTeste() throws Exception {
        try (var conexao = DriverManager.getConnection(System.getenv("VAGAS_TEST_DB_URL"), usuario(), senha());
             var comando = conexao.createStatement()) { comando.execute("DROP SCHEMA " + ESQUEMA + " CASCADE"); }
    }
    @Autowired CadastroService cadastro;
    @Autowired OcupacaoService ocupacao;
    @Autowired JdbcTemplate banco;
    @Autowired ObjectMapper conversor;
    @BeforeEach
    void limparRegistros() {
        banco.execute("TRUNCATE evento_pendente, mensagem_processada, reserva_ticket, vaga, bloco, tipo_vaga, setor CASCADE");
    }
    private VagaResponse cadastrarVaga() {
        var setor = cadastro.cadastrarSetor(new SetorRequest("A", CadastroStatus.ATIVO));
        var bloco = cadastro.cadastrarBloco(new BlocoRequest(setor.id(), "1", CadastroStatus.ATIVO));
        var tipo = cadastro.cadastrarTipo(new TipoVagaRequest("COMUM"));
        return cadastro.cadastrarVaga(new VagaRequest("A-1", bloco.id(), tipo.id()));
    }
    private MensagemEnvelope mensagem(UUID ticketId) {
        return new MensagemEnvelope(UUID.randomUUID(), ticketId, "RESERVAR_VAGA", Instant.now(),
                conversor.valueToTree(Map.of("ticketId", ticketId)));
    }
    @Test
    void devePersistirReservaInboxEOutboxAtomicamente() {
        var vaga = cadastrarVaga(); UUID ticketId = UUID.randomUUID(); var mensagem = mensagem(ticketId);
        ocupacao.reservar(mensagem, ticketId); ocupacao.reservar(mensagem, ticketId);
        assertThat(cadastro.consultarVaga(vaga.id()).ticketId()).isEqualTo(ticketId);
        assertThat(banco.queryForObject("SELECT count(*) FROM evento_pendente", Integer.class)).isEqualTo(1);
        assertThat(banco.queryForObject("SELECT count(*) FROM mensagem_processada", Integer.class)).isEqualTo(1);
        var resposta = conversor.readValue(banco.queryForObject("SELECT envelope FROM evento_pendente", String.class),
                MensagemEnvelope.class);
        assertThat(resposta.tipo()).isEqualTo("VAGA_RESERVADA");
        assertThat(resposta.correlacaoId()).isEqualTo(ticketId);
    }
    @Test
    void deveReverterInboxQuandoLiberacaoForInvalida() {
        var vaga = cadastrarVaga(); UUID ticketId = UUID.randomUUID();
        ocupacao.reservar(mensagem(ticketId), ticketId);
        var liberacao = mensagem(ticketId);
        assertThatThrownBy(() -> ocupacao.liberar(liberacao, ticketId, UUID.randomUUID()))
                .isInstanceOf(org.springframework.amqp.AmqpRejectAndDontRequeueException.class);
        assertThat(banco.queryForObject("SELECT count(*) FROM mensagem_processada WHERE id = ?",
                Integer.class, liberacao.mensagemId())).isZero();
        assertThat(cadastro.consultarVaga(vaga.id()).ticketId()).isEqualTo(ticketId);
        ocupacao.liberar(liberacao, ticketId, vaga.id());
        assertThat(cadastro.consultarVaga(vaga.id()).status()).isEqualTo(VagaStatus.LIVRE);
    }
    @Test
    void deveReservarUmaUnicaVagaSobConcorrencia() throws Exception {
        cadastrarVaga();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Callable<Void>> tarefas = new ArrayList<>();
            for (int indice = 0; indice < 8; indice++) tarefas.add(() -> {
                UUID ticketId = UUID.randomUUID(); ocupacao.reservar(mensagem(ticketId), ticketId); return null;
            });
            for (var resultado : executor.invokeAll(tarefas)) resultado.get(15, TimeUnit.SECONDS);
        }
        assertThat(cadastro.consultarOcupacao().ocupadas()).isEqualTo(1);
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
                ocupacao.reservar(mensagem(ticketId), ticketId); return null;
            });
            for (var resultado : executor.invokeAll(tarefas)) resultado.get(15, TimeUnit.SECONDS);
        }
        assertThat(cadastro.consultarVaga(vaga.id()).ticketId()).isEqualTo(ticketId);
        assertThat(banco.queryForObject("SELECT count(*) FROM reserva_ticket", Integer.class)).isEqualTo(1);
    }
    @Test
    void deveExcluirSetorInativoDaSelecao() {
        var vaga = cadastrarVaga(); var bloco = cadastro.consultarBloco(vaga.blocoId());
        cadastro.atualizarSetor(bloco.setorId(), new SetorRequest("A", CadastroStatus.INATIVO));
        UUID ticketId = UUID.randomUUID(); ocupacao.reservar(mensagem(ticketId), ticketId);
        assertThat(cadastro.consultarVaga(vaga.id()).status()).isEqualTo(VagaStatus.LIVRE);
        assertThat(cadastro.consultarOcupacao().disponiveis()).isZero();
    }
}
