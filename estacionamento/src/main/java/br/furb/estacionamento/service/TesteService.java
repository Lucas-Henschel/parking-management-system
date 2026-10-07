package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TesteResponse;
import br.furb.estacionamento.dto.TesteResponse.PagamentoTeste;
import br.furb.estacionamento.dto.TesteResponse.Evidencia;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

/** Orquestra apenas a simulação; as reservas e liberações continuam passando pelo RabbitMQ. */
@Service
public class TesteService {
    private static final Logger log = LoggerFactory.getLogger(TesteService.class);
    private final RestClient estacionamento;
    private final RestClient vagas;
    private final RestClient pagamento;
    private final int esperaSegundos;
    private final AtomicBoolean executando = new AtomicBoolean();

    public TesteService(
        @Value("${teste.estacionamento-url:http://localhost:${server.port:8081}}") String estacionamentoUrl,
        @Value("${teste.vagas-url:http://localhost:8082}") String vagasUrl,
        @Value("${teste.pagamento-url:http://localhost:8083}") String pagamentoUrl,
        @Value("${teste.espera-segundos:30}") int esperaSegundos
    ) {
        this.estacionamento = cliente(estacionamentoUrl);
        this.vagas = cliente(vagasUrl);
        this.pagamento = cliente(pagamentoUrl);
        this.esperaSegundos = esperaSegundos;
    }

    private RestClient cliente(String url) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(Duration.ofSeconds(5));
        fabrica.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().baseUrl(url).requestFactory(fabrica).build();
    }

    public TesteResponse executar() {
        if (!executando.compareAndSet(false, true)) {
            throw new EstadoInvalidoException("Já existe uma simulação em andamento");
        }
        try {
            return new Execucao().executar();
        } finally {
            executando.set(false);
        }
    }

    private class Execucao {
        private final String execucao = UUID.randomUUID().toString().substring(0, 8);
        private final List<Evidencia> evidencias = new ArrayList<>();
        private String etapa = "Disponibilidade dos serviços";

        private TesteResponse executar() {
            var etapas = new ArrayList<String>();
            var tickets = new ArrayList<UUID>();
            var pagamentos = new ArrayList<PagamentoTeste>();
            String erro = null;
            try {
                for (RestClient cliente : List.of(estacionamento, vagas, pagamento)) {
                    JsonNode saude = consultar(cliente, "/actuator/health");
                    exigir("UP".equals(saude.path("status").asText()), "Um dos serviços não está saudável");
                }
                etapas.add("Os três serviços estão disponíveis");
                etapa = "Antes do cadastro da estrutura";
                listar("/setores");
                listar("/tipos-vaga");
                listar("/blocos");
                listar("/vagas");
                etapa = "Cadastro e consulta da estrutura";
                prepararVagas(execucao);
                etapas.add("Criados 2 setores, 3 categorias, 6 blocos e 12 vagas");

                var vagasReservadas = new HashSet<String>();
                for (int i = 0; i < 8; i++) {
                    exigir(vagasReservadas.add(entrar(tickets)), "Dois carros receberam a mesma vaga");
                }
                etapas.add("8 carros entraram e receberam vagas distintas");

                String[] metodos = {"PIX", "DINHEIRO", "CARTAO_CREDITO"};
                var metodosRegistrados = new HashSet<String>();
                for (int i = 0; i < metodos.length; i++) {
                    UUID ticketId = tickets.get(i);
                    etapa = "Antes da saída do ticket " + ticketId;
                    String vagaId = consultar(estacionamento, "/tickets/" + ticketId).path("vagaId").asText();
                    exigir("OCUPADA".equals(consultar(vagas, "/vagas/" + vagaId).path("status").asText()),
                        "A vaga deve estar ocupada antes da saída");
                    etapa = "Saída e cálculo do ticket " + ticketId;
                    enviar(estacionamento, "/tickets/" + ticketId + "/saida", Map.of());
                    aguardar(() -> statusTicket(ticketId, "AGUARDANDO_PAGAMENTO"), "Cálculo do ticket " + ticketId);
                    etapa = "Antes do pagamento do ticket " + ticketId;
                    JsonNode calculado = consultar(pagamento, "/pagamentos/ticket/" + ticketId);
                    exigir("CALCULADO".equals(calculado.path("status").asText()), "Pagamento não está calculado");
                    JsonNode ticketCalculado = consultar(estacionamento, "/tickets/" + ticketId);
                    exigir(calculado.path("valor").decimalValue().compareTo(ticketCalculado.path("valor").decimalValue()) == 0,
                        "Valor do pagamento diverge do valor do ticket");
                    etapa = "Pagamento com " + metodos[i] + " do ticket " + ticketId;
                    JsonNode recebido = enviar(pagamento, "/pagamentos/ticket/" + ticketId + "/pagar",
                        Map.of("metodo", metodos[i]));
                    exigir("PAGO".equals(recebido.path("status").asText()), "Pagamento não confirmado: " + ticketId);
                    exigir(ticketId.toString().equals(recebido.path("ticketId").asText()), "Pagamento de outro ticket");
                    String metodoId = recebido.path("metodoPagamentoId").asText();
                    exigir(!metodoId.isBlank() && !"null".equals(metodoId) && metodosRegistrados.add(metodoId),
                        "Os pagamentos devem usar métodos distintos");
                    pagamentos.add(new PagamentoTeste(metodos[i], recebido));
                    JsonNode pago = consultar(pagamento, "/pagamentos/ticket/" + ticketId);
                    exigir("PAGO".equals(pago.path("status").asText())
                        && metodoId.equals(pago.path("metodoPagamentoId").asText()), "Pagamento não persistiu como esperado");
                    etapa = "Depois do pagamento do ticket " + ticketId;
                    aguardar(() -> statusTicket(ticketId, "FINALIZADO"), "Finalização do ticket " + ticketId);
                    aguardar(() -> "LIVRE".equals(consultar(vagas, "/vagas/" + vagaId).path("status").asText()),
                        "Liberação da vaga " + vagaId);
                    etapas.add("Ticket " + ticketId + " pago com " + metodos[i] + ", finalizado e vaga liberada");
                }

                etapa = "Antes de preencher o estacionamento";
                int restantes = contarVagasLivres();
                for (int i = 0; i < restantes; i++) entrar(tickets);
                etapa = "Depois de preencher o estacionamento";
                exigir(contarVagasLivres() == 0, "Ainda há vagas disponíveis após as novas entradas");
                etapas.add("Mais " + restantes + " carros entraram; não há vagas livres em setores e blocos ativos");
                validarTresTentativas(tickets);
                etapas.add("Carro excedente recusado após 3 tentativas, com saída, sem vaga ou pagamento; não retomou as tentativas");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                erro = "Simulação interrompida";
            } catch (RestClientResponseException exception) {
                erro = "Falha HTTP " + exception.getStatusCode() + ": " + exception.getResponseBodyAsString();
            } catch (RuntimeException exception) {
                erro = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            }
            log.info("[teste {}] Fim: sucesso={}, erro={}", execucao, erro == null, erro);
            return new TesteResponse(erro == null, execucao, etapas, tickets, pagamentos, evidencias, erro,
                "Dados criados são mantidos. Os logs do serviço vagas mostram cada busca com ticketId e messageId.");
        }

        private void validarTresTentativas(List<UUID> tickets) throws InterruptedException {
            etapa = "Entrada com estacionamento cheio";
            String placa = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            UUID id = UUID.fromString(enviar(estacionamento, "/entrada", Map.of("placa", placa)).path("id").asText());
            tickets.add(id);
            etapa = "Três tentativas sem vaga do ticket " + id;
            aguardar(() -> {
                JsonNode ticket = consultar(estacionamento, "/tickets/" + id);
                exigir(!"ATIVO".equals(ticket.path("status").asText()), "Carro excedente recebeu uma vaga");
                exigir(ticket.path("tentativasReserva").asInt() <= 3, "O ticket excedeu três tentativas");
                return "RECUSADO".equals(ticket.path("status").asText());
            }, "Recusa após três tentativas do ticket " + id);
            JsonNode recusado = consultar(estacionamento, "/tickets/" + id);
            exigir(recusado.path("tentativasReserva").asInt() == 3, "Ticket não completou três tentativas");
            exigir(recusado.path("vagaId").isNull() && recusado.path("valor").isNull()
                && !recusado.path("saida").isNull(), "Ticket recusado deve sair sem vaga e sem cobrança");
            etapa = "Verificação de parada das tentativas do ticket " + id;
            Thread.sleep(2000);
            JsonNode depois = consultar(estacionamento, "/tickets/" + id);
            exigir(recusado.equals(depois), "Ticket recusado foi alterado após encerrar as tentativas");
            exigir(contarVagasLivres() == 0, "Disponibilidade mudou durante o teste de estacionamento cheio");
            try {
                consultar(pagamento, "/pagamentos/ticket/" + id);
                throw new IllegalStateException("Foi criado pagamento para o ticket recusado");
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() != 404) throw exception;
            }
        }

        private void prepararVagas(String execucao) {
            var tipos = new ArrayList<String>();
            for (String nome : List.of("COMUM", "MOTO", "ACESSIVEL")) {
                tipos.add(cadastrar("/tipos-vaga", Map.of("nome", "TESTE-" + execucao + "-" + nome)).path("id").asText());
            }
            for (int setor = 1; setor <= 2; setor++) {
                String codigo = "TESTE-" + execucao + "-S" + setor;
                String setorId = cadastrar("/setores", Map.of("codigo", codigo, "status", "ATIVO")).path("id").asText();
                for (int bloco = 1; bloco <= 3; bloco++) {
                    String blocoId = cadastrar("/blocos", Map.of("setorId", setorId,
                        "codigo", codigo + "-B" + bloco, "status", "ATIVO")).path("id").asText();
                    for (int vaga = 1; vaga <= 2; vaga++) {
                        cadastrar("/vagas", Map.of("numero", codigo + "-B" + bloco + "-V" + vaga,
                            "blocoId", blocoId, "tipoId", tipos.get(bloco - 1)));
                    }
                }
            }
        }

        private JsonNode cadastrar(String rota, Map<String, ?> dados) {
            JsonNode criado = enviar(vagas, rota, dados);
            JsonNode consultado = consultar(vagas, rota + "/" + criado.path("id").asText());
            exigir(criado.equals(consultado), "Cadastro não persistiu como esperado: " + rota);
            return consultado;
        }

        private String entrar(List<UUID> tickets) throws InterruptedException {
            String placa = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
            etapa = "Antes da entrada da placa " + placa;
            int livresAntes = contarVagasLivres();
            exigir(livresAntes > 0, "Não há vaga livre para a entrada prevista");
            etapa = "Entrada e reserva da placa " + placa;
            UUID id = UUID.fromString(enviar(estacionamento, "/entrada", Map.of("placa", placa)).path("id").asText());
            tickets.add(id);
            aguardar(() -> {
                JsonNode ticket = consultar(estacionamento, "/tickets/" + id);
                exigir(!"RECUSADO".equals(ticket.path("status").asText()), "Entrada recusada: " + id);
                return "ATIVO".equals(ticket.path("status").asText());
            }, "Reserva do ticket " + id);
            String vagaId = consultar(estacionamento, "/tickets/" + id).path("vagaId").asText();
            JsonNode vaga = consultar(vagas, "/vagas/" + vagaId);
            exigir("OCUPADA".equals(vaga.path("status").asText()) && id.toString().equals(vaga.path("ticketId").asText()),
                "Vaga não foi ocupada pelo ticket " + id);
            etapa = "Depois da reserva do ticket " + id;
            exigir(contarVagasLivres() == livresAntes - 1, "A entrada deve ocupar exatamente uma vaga");
            return vagaId;
        }

        private int contarVagasLivres() {
            var setoresAtivos = new HashSet<String>();
            for (JsonNode setor : listar("/setores")) {
                if ("ATIVO".equals(setor.path("status").asText())) setoresAtivos.add(setor.path("id").asText());
            }
            var blocosAtivos = new HashSet<String>();
            for (JsonNode bloco : listar("/blocos")) {
                if ("ATIVO".equals(bloco.path("status").asText()) && setoresAtivos.contains(bloco.path("setorId").asText())) {
                    blocosAtivos.add(bloco.path("id").asText());
                }
            }
            return (int) listar("/vagas").stream().filter(vaga -> "LIVRE".equals(vaga.path("status").asText())
                && blocosAtivos.contains(vaga.path("blocoId").asText())).count();
        }

        private List<JsonNode> listar(String rota) {
            var itens = new ArrayList<JsonNode>();
            int pagina = 0;
            JsonNode resposta;
            do {
                resposta = consultar(vagas, rota + "?page=" + pagina++ + "&size=100");
                resposta.path("content").forEach(itens::add);
            } while (pagina < resposta.path("totalPages").asInt());
            return itens;
        }

        private boolean statusTicket(UUID id, String status) {
            return status.equals(consultar(estacionamento, "/tickets/" + id).path("status").asText());
        }

        private void aguardar(BooleanSupplier concluido, String etapa) throws InterruptedException {
            long inicio = System.nanoTime();
            long limite = System.nanoTime() + Duration.ofSeconds(esperaSegundos).toNanos();
            int consultas = 0;
            boolean confirmou = false;
            try {
                do {
                    consultas++;
                    confirmou = concluido.getAsBoolean();
                    if (!confirmou) {
                        log.info("[teste {}] Aguardando {}: consulta {} ainda não confirmou o resultado", execucao, etapa, consultas);
                        exigir(System.nanoTime() < limite, "Tempo esgotado: " + etapa + ". Confira os consumidores e o RabbitMQ.");
                        Thread.sleep(250);
                    }
                } while (!confirmou);
            } finally {
                registrar("RabbitMQ", "ESPERA", etapa, null, null,
                    Map.of("consultas", consultas, "duracaoMs", Duration.ofNanos(System.nanoTime() - inicio).toMillis(), "concluida", confirmou));
            }
        }

        private JsonNode consultar(RestClient cliente, String rota) {
            return chamar(cliente, "GET", rota, null);
        }

        private JsonNode enviar(RestClient cliente, String rota, Map<String, ?> dados) {
            return chamar(cliente, "POST", rota, dados);
        }

        private JsonNode chamar(RestClient cliente, String metodo, String rota, Map<String, ?> dados) {
            String servico = cliente == estacionamento ? "estacionamento" : cliente == vagas ? "vagas" : "pagamento";
            log.info("[teste {}] {} | {} {} {} | envio={}", execucao, etapa, servico, metodo, rota, dados);
            try {
                var resposta = dados == null ? cliente.get().uri(rota).retrieve().toEntity(JsonNode.class)
                    : cliente.post().uri(rota).contentType(MediaType.APPLICATION_JSON).body(dados).retrieve().toEntity(JsonNode.class);
                registrar(servico, metodo, rota, resposta.getStatusCode().value(), dados, resposta.getBody());
                return resposta.getBody();
            } catch (RestClientResponseException exception) {
                registrar(servico, metodo, rota, exception.getStatusCode().value(), dados, exception.getResponseBodyAsString());
                throw exception;
            } catch (RuntimeException exception) {
                registrar(servico, metodo, rota, null, dados, exception.toString());
                throw exception;
            }
        }

        private void registrar(String servico, String metodo, String rota, Integer status, Object dados, Object resultado) {
            evidencias.add(new Evidencia(Instant.now(), etapa, servico, metodo, rota, status, dados, resultado));
            log.info("[teste {}] {} | {} {} {} | HTTP={} | resultado={}", execucao, etapa, servico, metodo, rota, status, resultado);
        }

        private void exigir(boolean condicao, String mensagem) {
            if (!condicao) throw new IllegalStateException(mensagem);
        }
    }
}
