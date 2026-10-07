package br.furb.estacionamento.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.HashSet;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/** Exercita as chamadas HTTP do simulador, sem alterar bancos locais. */
class TesteServiceTest {
    private HttpServer servidor;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, List<Map<String, Object>>> cadastros = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> tickets = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> pagamentos = new LinkedHashMap<>();
    private final List<String> metodos = new ArrayList<>();
    private final HashSet<String> semVaga = new HashSet<>();
    private boolean saudavel = true;
    private boolean confirmarReserva = true;
    private int consultasPendentes;
    private TesteService teste;

    @BeforeEach
    void iniciar() throws IOException {
        for (String rota : List.of("/setores", "/tipos-vaga", "/blocos", "/vagas")) {
            cadastros.put(rota, new ArrayList<>());
        }
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/", this::responder);
        servidor.start();
        String url = "http://127.0.0.1:" + servidor.getAddress().getPort();
        teste = new TesteService(url, url, url, 1);
    }

    @AfterEach
    void parar() {
        servidor.stop(0);
    }

    @Test
    void criaEstruturaPagaPreencheERecusaCarroExcedenteAposTresTentativas() {
        consultasPendentes = 1;
        var resultado = teste.executar();
        assertTrue(resultado.sucesso(), resultado.erro());
        assertEquals(2, cadastros.get("/setores").size());
        assertEquals(3, cadastros.get("/tipos-vaga").size());
        assertEquals(6, cadastros.get("/blocos").size());
        assertEquals(12, cadastros.get("/vagas").size());
        for (var setor : cadastros.get("/setores")) {
            assertEquals(3, cadastros.get("/blocos").stream()
                .filter(bloco -> setor.get("id").equals(bloco.get("setorId"))).count());
        }
        assertEquals(List.of("PIX", "DINHEIRO", "CARTAO_CREDITO"), metodos);
        assertEquals(3, resultado.pagamentos().size());
        assertEquals(16, resultado.tickets().size());
        assertEquals(3, tickets.values().stream().filter(t -> "FINALIZADO".equals(t.get("status"))).count());
        assertTrue(cadastros.get("/vagas").stream().allMatch(v -> "OCUPADA".equals(v.get("status"))));
        assertEquals(12, cadastros.get("/vagas").stream().map(v -> v.get("ticketId")).distinct().count());
        assertEquals(1, tickets.values().stream().filter(t -> "RECUSADO".equals(t.get("status"))).count());
        var recusado = tickets.get(resultado.tickets().get(15).toString());
        assertEquals(3, recusado.get("tentativasReserva"));
        assertNotNull(recusado.get("saida"));
        assertNull(recusado.get("vagaId"));
        assertTrue(resultado.evidencias().stream().anyMatch(e -> e.servico().equals("pagamento")
            && Integer.valueOf(404).equals(e.statusHttp())));
        assertTrue(resultado.evidencias().stream().anyMatch(e -> e.metodo().equals("ESPERA")
            && ((Map<?, ?>) e.resultado()).get("consultas").equals(2)));
        assertTrue(resultado.evidencias().stream().anyMatch(e -> e.etapa().startsWith("Antes da entrada")
            && e.metodo().equals("GET") && e.rota().startsWith("/vagas?")));
        assertEquals(3, resultado.evidencias().stream().filter(e -> e.etapa().startsWith("Antes do pagamento")
            && e.servico().equals("pagamento") && e.metodo().equals("GET")).count());
        assertTrue(resultado.evidencias().stream().anyMatch(e -> e.metodo().equals("GET")
            && e.rota().startsWith("/tipos-vaga/")));
    }

    @Test
    void servicoIndisponivelFalhaAntesDeCriarDados() {
        saudavel = false;
        var resultado = teste.executar();
        assertFalse(resultado.sucesso());
        assertTrue(resultado.erro().contains("503"));
        assertTrue(cadastros.values().stream().allMatch(List::isEmpty));
        assertTrue(resultado.tickets().isEmpty());
        assertEquals(503, resultado.evidencias().get(0).statusHttp());
        saudavel = true;
        assertTrue(teste.executar().sucesso(), "Deve permitir nova execução após uma falha");
    }

    @Test
    void reservaSemRespostaInterrompeComProgressoParcialSemPagar() {
        confirmarReserva = false;
        var resultado = teste.executar();
        assertFalse(resultado.sucesso());
        assertTrue(resultado.erro().contains("Tempo esgotado: Reserva"));
        assertEquals(1, resultado.tickets().size());
        assertEquals(12, cadastros.get("/vagas").size());
        assertTrue(resultado.pagamentos().isEmpty());
        assertTrue(resultado.evidencias().stream().anyMatch(e -> e.rota().startsWith("/tickets/")
            && e.resultado().toString().contains("PENDENTE")));
    }

    private void responder(HttpExchange chamada) throws IOException {
        String rota = chamada.getRequestURI().getPath();
        boolean post = "POST".equals(chamada.getRequestMethod());
        Map<String, Object> dados = post ? mapper.readValue(chamada.getRequestBody(), Map.class) : Map.of();
        Object resposta;
        int status = 200;
        if (rota.equals("/actuator/health")) {
            status = saudavel ? 200 : 503;
            resposta = Map.of("status", saudavel ? "UP" : "DOWN");
        } else if (cadastros.containsKey(rota)) {
            if (post) {
                var criado = new LinkedHashMap<>(dados);
                criado.put("id", UUID.randomUUID().toString());
                if (rota.equals("/vagas")) criado.put("status", "LIVRE");
                cadastros.get(rota).add(criado);
                resposta = criado;
                status = 201;
            } else {
                resposta = Map.of("content", cadastros.get(rota), "totalPages", 1);
            }
        } else if (rota.equals("/entrada")) {
            String id = UUID.randomUUID().toString();
            var ticket = new LinkedHashMap<String, Object>();
            ticket.put("id", id);
            ticket.put("status", confirmarReserva ? "ATIVO" : "PENDENTE");
            ticket.put("tentativasReserva", 1);
            ticket.put("vagaId", null);
            ticket.put("valor", null);
            ticket.put("saida", null);
            if (confirmarReserva) {
                var vaga = cadastros.get("/vagas").stream().filter(v -> "LIVRE".equals(v.get("status")))
                    .findFirst();
                if (vaga.isPresent()) {
                    vaga.get().put("status", "OCUPADA");
                    vaga.get().put("ticketId", id);
                    ticket.put("vagaId", vaga.get().get("id"));
                } else {
                    semVaga.add(id);
                    ticket.put("status", "PENDENTE");
                }
            }
            tickets.put(id, ticket);
            resposta = ticket;
            status = 202;
        } else if (rota.startsWith("/tickets/")) {
            var ticket = tickets.get(rota.split("/")[2]);
            if (!post && semVaga.contains(ticket.get("id")) && "PENDENTE".equals(ticket.get("status"))) {
                int tentativas = (int) ticket.get("tentativasReserva");
                if (tentativas < 3) ticket.put("tentativasReserva", tentativas + 1);
                else {
                    ticket.put("status", "RECUSADO");
                    ticket.put("saida", Instant.now().toString());
                }
            }
            if (post) {
                ticket.put("status", "AGUARDANDO_PAGAMENTO");
                ticket.put("valor", 10);
                pagamentos.put(ticket.get("id").toString(), new LinkedHashMap<>(Map.of("id", UUID.randomUUID().toString(),
                    "ticketId", ticket.get("id"), "valor", 10, "status", "CALCULADO")));
            }
            resposta = ticket;
            if (!post && consultasPendentes > 0) {
                consultasPendentes--;
                var pendente = new LinkedHashMap<>(ticket);
                pendente.put("status", "PENDENTE");
                resposta = pendente;
            }
        } else if (cadastros.keySet().stream().anyMatch(r -> rota.startsWith(r + "/"))) {
            String cadastro = "/" + rota.split("/")[1];
            resposta = cadastros.get(cadastro).stream().filter(v -> rota.endsWith(v.get("id").toString())).findFirst().orElseThrow();
        } else if (rota.startsWith("/pagamentos/ticket/")) {
            String id = rota.split("/")[3];
            var pagamento = pagamentos.get(id);
            if (post) {
                String metodo = dados.get("metodo").toString();
                metodos.add(metodo);
                tickets.get(id).put("status", "FINALIZADO");
                cadastros.get("/vagas").stream().filter(v -> id.equals(v.get("ticketId"))).forEach(v -> {
                    v.put("status", "LIVRE");
                    v.remove("ticketId");
                });
                pagamento.put("metodoPagamentoId", UUID.randomUUID().toString());
                pagamento.put("status", "PAGO");
            }
            resposta = pagamento;
            if (pagamento == null) {
                status = 404;
                resposta = Map.of("erro", "Pagamento não encontrado");
            }
        } else {
            status = 404;
            resposta = Map.of("erro", rota);
        }
        byte[] corpo = mapper.writeValueAsBytes(resposta);
        chamada.getResponseHeaders().set("Content-Type", "application/json");
        chamada.sendResponseHeaders(status, corpo.length);
        try (var saida = chamada.getResponseBody()) { saida.write(corpo); }
    }
}
