package br.furb.estacionamento.service;

import br.furb.estacionamento.PostgresTestConfiguration;
import br.furb.estacionamento.dto.*;
import br.furb.estacionamento.enums.MotivoIndisponibilidade;
import br.furb.estacionamento.enums.PagamentoStatus;
import br.furb.estacionamento.enums.ResultadoPublicacao;
import br.furb.estacionamento.enums.TicketStatus;
import br.furb.estacionamento.enums.TipoMensagem;
import br.furb.estacionamento.messaging.TicketOutboxPublisher;
import br.furb.estacionamento.entity.EventoPendente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.ArrayList;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {"spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.dynamic=false", "estacionamento.outbox.habilitada=false"})
@Import(PostgresTestConfiguration.class)
class TicketIntegrationTest {
    @Autowired TicketService tickets;
    @Autowired ResultadoVagaService vagas;
    @Autowired PagamentoTicketService pagamentos;
    @Autowired PublicacaoOutboxService outbox;
    @Autowired JdbcTemplate banco;
    @Autowired PlatformTransactionManager transacoes;
    @MockitoBean TicketOutboxPublisher publisher;

    @BeforeEach
    void limpar() {
        banco.execute("TRUNCATE evento_pendente, mensagem_processada, ticket, veiculo CASCADE");
    }

    private UUID ticketComSaida() {
        UUID id = tickets.registrarEntrada("ABC1234").id();
        vagas.registrarVagaConfirmada(id, UUID.randomUUID());
        tickets.registrarSaida(id);
        return id;
    }

    private <T> MensagemEnvelope<T> mensagem(UUID ticketId, TipoMensagem tipo, T payload) {
        return new MensagemEnvelope<>(UUID.randomUUID(), ticketId, tipo, Instant.now(), payload);
    }

    private int contar(String tabela) {
        return banco.queryForObject("SELECT count(*) FROM " + tabela, Integer.class);
    }

    @Test
    void rollbackDesfazTicketVeiculoEOutboxJuntos() {
        new TransactionTemplate(transacoes).executeWithoutResult(tx -> {
            tickets.registrarEntrada("ABC1234");
            assertEquals(1, contar("evento_pendente"));
            tx.setRollbackOnly();
        });
        assertEquals(0, contar("ticket"));
        assertEquals(0, contar("veiculo"));
        assertEquals(0, contar("evento_pendente"));
    }

    @Test
    void entradasConcorrentesDaMesmaPlacaCriamUmTicketEUmEvento() throws Exception {
        var largada = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(8);
        try {
            var resultados = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < 8; i++) {
                resultados.add(executor.submit(() -> {
                    largada.await();
                    try { tickets.registrarEntrada("ABC1234"); return true; }
                    catch (org.springframework.dao.DataIntegrityViolationException
                            | br.furb.estacionamento.exception.ConflitoNegocioException conflito) { return false; }
                }));
            }
            largada.countDown();
            int sucessos = 0;
            for (var resultado : resultados) if (resultado.get(20, TimeUnit.SECONDS)) sucessos++;
            assertEquals(1, sucessos);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, contar("ticket"));
        assertEquals(1, contar("evento_pendente"));
    }

    @Test
    void confirmacaoAntecipadaEDuplicatasFinalizamUmaVez() {
        UUID id = ticketComSaida();
        UUID pagamentoId = UUID.randomUUID();
        var confirmado = mensagem(id, TipoMensagem.PAGAMENTO_CONFIRMADO,
                new PagamentoConfirmadoPayload(pagamentoId, id, new BigDecimal("20.00"), "PIX", PagamentoStatus.PAGO));
        pagamentos.processarPagamentoConfirmado(confirmado);
        pagamentos.processarPagamentoConfirmado(confirmado);
        assertEquals(TicketStatus.ATIVO, tickets.buscarPorId(id).status());
        assertEquals(2, contar("evento_pendente"));
        var calculado = mensagem(id, TipoMensagem.PAGAMENTO_CALCULADO,
                new PagamentoCalculadoPayload(pagamentoId, id, new BigDecimal("20.00"), PagamentoStatus.CALCULADO));
        pagamentos.processarPagamentoCalculado(calculado);
        pagamentos.processarPagamentoCalculado(calculado);
        pagamentos.processarPagamentoConfirmado(mensagem(id, TipoMensagem.PAGAMENTO_CONFIRMADO, confirmado.payload()));
        pagamentos.processarPagamentoCalculado(mensagem(id, TipoMensagem.PAGAMENTO_CALCULADO, calculado.payload()));
        tickets.registrarSaida(id);
        assertEquals(TicketStatus.FINALIZADO, tickets.buscarPorId(id).status());
        assertEquals(3, contar("evento_pendente"));
    }

    @Test
    void valorDivergenteDesfazInboxEMantemConfirmacaoAntecipada() {
        UUID id = ticketComSaida();
        UUID pagamentoId = UUID.randomUUID();
        pagamentos.confirmarPagamento(id, pagamentoId, new BigDecimal("20.00"));
        var errado = mensagem(id, TipoMensagem.PAGAMENTO_CALCULADO,
                new PagamentoCalculadoPayload(pagamentoId, id, new BigDecimal("10.00"), PagamentoStatus.CALCULADO));
        assertThrows(br.furb.estacionamento.exception.ConflitoNegocioException.class,
                () -> pagamentos.processarPagamentoCalculado(errado));
        assertEquals(0, contar("mensagem_processada"));
        assertEquals(TicketStatus.ATIVO, tickets.buscarPorId(id).status());
        assertNull(tickets.buscarPorId(id).valor());
        assertEquals(2, contar("evento_pendente"));
        pagamentos.registrarPagamentoCalculado(id, pagamentoId, new BigDecimal("20.00"));
        assertEquals(TicketStatus.FINALIZADO, tickets.buscarPorId(id).status());
    }

    @Test
    void valoresInvalidosNaoAlteramTicketNemInbox() {
        UUID id = ticketComSaida();
        for (BigDecimal valor : new BigDecimal[]{null, new BigDecimal("-1"), new BigDecimal("1.001")}) {
            var invalido = mensagem(id, TipoMensagem.PAGAMENTO_CALCULADO,
                    new PagamentoCalculadoPayload(UUID.randomUUID(), id, valor, PagamentoStatus.CALCULADO));
            assertThrows(IllegalArgumentException.class,
                    () -> pagamentos.processarPagamentoCalculado(invalido));
        }
        assertEquals(0, contar("mensagem_processada"));
        assertNull(tickets.buscarPorId(id).valor());
    }

    @Test
    void falhaNoBrokerMantemEventoEReenviaMesmoMessageId() {
        tickets.registrarEntrada("ABC1234");
        doThrow(new IllegalStateException("broker indisponível")).doNothing().when(publisher).publicar(any());
        assertEquals(ResultadoPublicacao.FALHOU, outbox.publicarProximo());
        assertNull(banco.queryForObject("SELECT publicado_em FROM evento_pendente", Object.class));
        assertEquals(1, banco.queryForObject("SELECT tentativas FROM evento_pendente", Integer.class));
        assertEquals(ResultadoPublicacao.SEM_EVENTO, outbox.publicarProximo());
        banco.update("UPDATE evento_pendente SET proxima_tentativa = now() - interval '1 second'");
        assertEquals(ResultadoPublicacao.PUBLICADO, outbox.publicarProximo());
        var eventos = org.mockito.ArgumentCaptor.forClass(EventoPendente.class);
        verify(publisher, times(2)).publicar(eventos.capture());
        assertEquals(eventos.getAllValues().get(0).obterId(), eventos.getAllValues().get(1).obterId());
        assertEquals(eventos.getAllValues().get(0).obterEnvelope(), eventos.getAllValues().get(1).obterEnvelope());
        assertEquals(ResultadoPublicacao.SEM_EVENTO, outbox.publicarProximo());
    }

    @Test
    void pagamentosConcorrentesGeramUmaLiberacao() throws Exception {
        UUID id = ticketComSaida(); UUID pagamentoId = UUID.randomUUID();
        pagamentos.registrarPagamentoCalculado(id, pagamentoId, new BigDecimal("20.00"));
        var executor = Executors.newFixedThreadPool(4);
        try {
            var resultados = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) resultados.add(executor.submit(() ->
                    pagamentos.confirmarPagamento(id, pagamentoId, new BigDecimal("20.00"))));
            for (var resultado : resultados) resultado.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(TicketStatus.FINALIZADO, tickets.buscarPorId(id).status());
        assertEquals(3, contar("evento_pendente"));
    }

    @Test
    void constraintImpedeDoisTicketsAbertosParaVeiculoJaExistente() throws Exception {
        banco.update("INSERT INTO veiculo (id, placa) VALUES (?, ?)", UUID.randomUUID(), "ABC1234");
        entradasConcorrentesDaMesmaPlacaCriamUmTicketEUmEvento();
        assertEquals(1, contar("veiculo"));
    }

    @Test
    void consumidoresConcorrentesDaMesmaMensagemGravamUmaInbox() throws Exception {
        UUID id = ticketComSaida();
        var calculado = mensagem(id, TipoMensagem.PAGAMENTO_CALCULADO,
                new PagamentoCalculadoPayload(UUID.randomUUID(), id, new BigDecimal("20.00"), PagamentoStatus.CALCULADO));
        var executor = Executors.newFixedThreadPool(4);
        try {
            var resultados = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) resultados.add(executor.submit(() -> pagamentos.processarPagamentoCalculado(calculado)));
            for (var resultado : resultados) resultado.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(1, contar("mensagem_processada"));
        assertEquals(TicketStatus.AGUARDANDO_PAGAMENTO, tickets.buscarPorId(id).status());
    }

    @Test
    void publicadoresConcorrentesNaoDisputamMesmoEvento() throws Exception {
        tickets.registrarEntrada("ABC1234");
        var executor = Executors.newFixedThreadPool(4);
        try {
            var resultados = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) resultados.add(executor.submit(() -> outbox.publicarProximo()));
            for (var resultado : resultados) resultado.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        verify(publisher, times(1)).publicar(any());
        assertEquals(ResultadoPublicacao.SEM_EVENTO, outbox.publicarProximo());
    }

    @Test
    void pagamentoComIdentificadorDivergenteNaoFinalizaTicket() {
        UUID id = ticketComSaida();
        pagamentos.registrarPagamentoCalculado(id, UUID.randomUUID(), new BigDecimal("20.00"));
        assertThrows(br.furb.estacionamento.exception.ConflitoNegocioException.class,
                () -> pagamentos.confirmarPagamento(id, UUID.randomUUID(), new BigDecimal("20.00")));
        assertEquals(TicketStatus.AGUARDANDO_PAGAMENTO, tickets.buscarPorId(id).status());
        assertEquals(2, contar("evento_pendente"));
    }

    @Test
    void tresBuscasSemVagaEncerramTicketSemCobrarOuLiberarVaga() {
        UUID id = tickets.registrarEntrada("ABC1234").id();
        assertEquals(1, tickets.buscarPorId(id).tentativasReserva());
        for (int tentativa = 1; tentativa <= 3; tentativa++) {
            var indisponivel = mensagem(id, TipoMensagem.VAGA_INDISPONIVEL, new VagaResultadoPayload(id, null, null, MotivoIndisponibilidade.SEM_VAGAS));
            vagas.processarResultadoVaga(indisponivel);
            vagas.processarResultadoVaga(indisponivel);
            var ticket = tickets.buscarPorId(id);
            assertEquals(Math.min(tentativa + 1, 3), ticket.tentativasReserva());
            assertEquals(tentativa < 3 ? TicketStatus.PENDENTE : TicketStatus.RECUSADO, ticket.status());
            assertEquals(Math.min(tentativa + 1, 3), contar("evento_pendente"));
            if (tentativa < 3) assertNull(ticket.saida());
        }
        var ticket = tickets.buscarPorId(id);
        assertNotNull(ticket.saida());
        assertNull(ticket.valor());
        assertNull(ticket.vagaId());
        assertEquals(3, contar("mensagem_processada"));
        assertEquals(0, banco.queryForObject("SELECT count(*) FROM evento_pendente WHERE rota <> 'vaga.reservar'", Integer.class));
        vagas.processarResultadoVaga(mensagem(id, TipoMensagem.VAGA_INDISPONIVEL, new VagaResultadoPayload(id, null, null, MotivoIndisponibilidade.SEM_VAGAS)));
        assertEquals(3, contar("evento_pendente"));
        UUID novaEntrada = tickets.registrarEntrada("ABC1234").id();
        assertNotEquals(id, novaEntrada);
        assertEquals(1, tickets.buscarPorId(novaEntrada).tentativasReserva());
    }

    @Test
    void vagaEncontradaNaTerceiraBuscaAtivaMesmoTicket() {
        UUID id = tickets.registrarEntrada("ABC1234").id();
        for (int i = 0; i < 2; i++) {
            vagas.processarResultadoVaga(mensagem(id, TipoMensagem.VAGA_INDISPONIVEL,
                    new VagaResultadoPayload(id, null, null, MotivoIndisponibilidade.SEM_VAGAS)));
        }
        UUID vagaId = UUID.randomUUID();
        vagas.processarResultadoVaga(mensagem(id, TipoMensagem.VAGA_RESERVADA, new VagaResultadoPayload(id, vagaId, "A-1", null)));
        var ticket = tickets.buscarPorId(id);
        assertEquals(TicketStatus.ATIVO, ticket.status());
        assertEquals(3, ticket.tentativasReserva());
        assertEquals(vagaId, ticket.vagaId());
        assertNull(ticket.saida());
        assertEquals(3, contar("evento_pendente"));
        var comandos = banco.queryForList("SELECT envelope::json->>'messageId' FROM evento_pendente", String.class);
        assertEquals(3, comandos.stream().distinct().count());
        assertEquals(1, banco.queryForObject("SELECT count(DISTINCT envelope::json->>'correlationId') FROM evento_pendente", Integer.class));
    }

    @Test
    void respostaDuplicadaConcorrenteSolicitaSomenteUmaNovaBusca() throws Exception {
        UUID id = tickets.registrarEntrada("ABC1234").id();
        var indisponivel = mensagem(id, TipoMensagem.VAGA_INDISPONIVEL, new VagaResultadoPayload(id, null, null, MotivoIndisponibilidade.SEM_VAGAS));
        var executor = Executors.newFixedThreadPool(4);
        try {
            var resultados = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) resultados.add(executor.submit(() -> vagas.processarResultadoVaga(indisponivel)));
            for (var resultado : resultados) resultado.get(20, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertEquals(2, tickets.buscarPorId(id).tentativasReserva());
        assertEquals(2, contar("evento_pendente"));
        assertEquals(1, contar("mensagem_processada"));
    }

    @Test
    void ticketFinalizadoNoServicoDeVagasNaoGeraNovasTentativas() {
        UUID id = tickets.registrarEntrada("ABC1234").id();
        vagas.processarResultadoVaga(mensagem(id, TipoMensagem.VAGA_INDISPONIVEL,
                new VagaResultadoPayload(id, null, null, MotivoIndisponibilidade.TICKET_FINALIZADO)));
        assertEquals(TicketStatus.RECUSADO, tickets.buscarPorId(id).status());
        assertNotNull(tickets.buscarPorId(id).saida());
        assertEquals(1, contar("evento_pendente"));
    }
}
