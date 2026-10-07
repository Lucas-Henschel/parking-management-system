package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TesteResponse;
import br.furb.estacionamento.dto.TesteResponse.PagamentoTeste;
import br.furb.estacionamento.exception.EstadoInvalidoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** Orquestra apenas a simulação; as reservas e liberações continuam passando pelo RabbitMQ. */
@Service
public class TesteService {
    private static final Logger log = LoggerFactory.getLogger(TesteService.class);
    private static final Duration TIMEOUT_HTTP = Duration.ofSeconds(5);
    private static final int ENTRADAS_INICIAIS = 8;
    private static final String[] METODOS_PAGAMENTO = {"PIX", "DINHEIRO", "CARTAO_CREDITO"};
    private static final int TENTATIVAS_RESERVA = 3;

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

    private static RestClient cliente(String url) {
        var fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(TIMEOUT_HTTP);
        fabrica.setReadTimeout(TIMEOUT_HTTP);
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

    /** Uma execução da simulação, com o seu identificador, o seu progresso e as suas evidências. */
    private class Execucao {
        private final String id = UUID.randomUUID().toString().substring(0, 8);
        private final SimulacaoHttp http =
            new SimulacaoHttp(id, estacionamento, vagas, pagamento, esperaSegundos, "Disponibilidade dos serviços");
        private final List<String> etapas = new ArrayList<>();
        private final List<UUID> tickets = new ArrayList<>();
        private final List<PagamentoTeste> pagamentos = new ArrayList<>();

        TesteResponse executar() {
            String erro = null;

            try {
                verificarServicos();
                exigirBancoSemVagasLivres();
                prepararEstrutura();
                fazerEntradasIniciais();
                fazerSaidasComPagamento();
                preencherEstacionamento();
                validarRecusaAposTresTentativas();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                erro = "Simulação interrompida";
            } catch (RestClientResponseException exception) {
                erro = "Falha HTTP " + exception.getStatusCode() + ": " + exception.getResponseBodyAsString();
            } catch (RuntimeException exception) {
                erro = exception.getMessage() != null ? exception.getMessage() : exception.getClass().getSimpleName();
            }

            log.info("[teste {}] Fim: sucesso={}, erro={}", id, erro == null, erro);

            return new TesteResponse(
                erro == null,
                id,
                etapas,
                tickets,
                pagamentos,
                http.evidencias(),
                erro,
            "Dados criados são mantidos. Os logs do serviço vagas mostram cada busca com ticketId e messageId."
            );
        }

        // ---- Etapas da simulação ----

        private void verificarServicos() {
            for (RestClient cliente : List.of(estacionamento, vagas, pagamento)) {
                JsonNode saude = http.consultar(cliente, "/actuator/health");
                exigir("UP".equals(saude.path("status").asText()), "Um dos serviços não está saudável");
            }

            etapas.add("Os três serviços estão disponíveis");
        }

        /** As vagas livres de outros dados seriam ocupadas pela simulação e quebrariam as contagens. */
        private void exigirBancoSemVagasLivres() {
            http.etapa("Antes do cadastro da estrutura");
            int livresExistentes = contarVagasLivres();

            exigir(livresExistentes == 0, "Há " + livresExistentes + " vagas livres que não pertencem à simulação; "
                + "ela exige um banco sem vagas livres para não ocupar vagas de outros dados");
            for (String rota : List.of("/setores", "/tipos-vaga", "/blocos", "/vagas")) {
                listarVagas(rota);
            }
        }

        private void prepararEstrutura() {
            http.etapa("Cadastro e consulta da estrutura");
            var tipos = new ArrayList<String>();

            for (String nome : List.of("COMUM", "MOTO", "ACESSIVEL")) {
                tipos.add(cadastrar("/tipos-vaga", Map.of("nome", "TESTE-" + id + "-" + nome)).path("id").asText());
            }

            for (int setor = 1; setor <= 2; setor++) {
                String codigo = "TESTE-" + id + "-S" + setor;
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

            etapas.add("Criados 2 setores, 3 categorias, 6 blocos e 12 vagas");
        }

        private void fazerEntradasIniciais() throws InterruptedException {
            var vagasReservadas = new HashSet<String>();

            for (int i = 0; i < ENTRADAS_INICIAIS; i++) {
                exigir(vagasReservadas.add(entrar()), "Dois carros receberam a mesma vaga");
            }

            etapas.add(ENTRADAS_INICIAIS + " carros entraram e receberam vagas distintas");
        }

        private void fazerSaidasComPagamento() throws InterruptedException {
            var metodosRegistrados = new HashSet<String>();

            for (int i = 0; i < METODOS_PAGAMENTO.length; i++) {
                sairEPagar(tickets.get(i), METODOS_PAGAMENTO[i], metodosRegistrados);
            }
        }

        private void preencherEstacionamento() throws InterruptedException {
            http.etapa("Antes de preencher o estacionamento");
            int restantes = contarVagasLivres();
            for (int i = 0; i < restantes; i++) entrar();
            http.etapa("Depois de preencher o estacionamento");
            exigir(contarVagasLivres() == 0, "Ainda há vagas disponíveis após as novas entradas");
            etapas.add("Mais " + restantes + " carros entraram; não há vagas livres em setores e blocos ativos");
        }

        private void validarRecusaAposTresTentativas() throws InterruptedException {
            http.etapa("Entrada com estacionamento cheio");
            UUID ticketId = registrarEntrada(novaPlaca());
            http.etapa("Três tentativas sem vaga do ticket " + ticketId);
            http.aguardar(() -> {
                JsonNode ticket = consultarTicket(ticketId);
                exigir(!"ATIVO".equals(ticket.path("status").asText()), "Carro excedente recebeu uma vaga");
                exigir(ticket.path("tentativasReserva").asInt() <= TENTATIVAS_RESERVA, "O ticket excedeu três tentativas");
                return "RECUSADO".equals(ticket.path("status").asText());
            }, "Recusa após três tentativas do ticket " + ticketId);

            JsonNode recusado = consultarTicket(ticketId);
            exigir(recusado.path("tentativasReserva").asInt() == TENTATIVAS_RESERVA, "Ticket não completou três tentativas");
            exigir(recusado.path("vagaId").isNull() && recusado.path("valor").isNull()
                && !recusado.path("saida").isNull(), "Ticket recusado deve sair sem vaga e sem cobrança");

            http.etapa("Verificação de parada das tentativas do ticket " + ticketId);
            Thread.sleep(2000);
            exigir(recusado.equals(consultarTicket(ticketId)), "Ticket recusado foi alterado após encerrar as tentativas");
            exigir(contarVagasLivres() == 0, "Disponibilidade mudou durante o teste de estacionamento cheio");
            exigirSemPagamento(ticketId);
            etapas.add("Carro excedente recusado após 3 tentativas, com saída, sem vaga ou pagamento; não retomou as tentativas");
        }

        // ---- Passos de uma entrada e de uma saída ----

        /** Entra com um carro, espera a reserva e devolve o id da vaga recebida. */
        private String entrar() throws InterruptedException {
            String placa = novaPlaca();
            http.etapa("Antes da entrada da placa " + placa);
            int livresAntes = contarVagasLivres();
            exigir(livresAntes > 0, "Não há vaga livre para a entrada prevista");

            http.etapa("Entrada e reserva da placa " + placa);
            UUID ticketId = registrarEntrada(placa);
            http.aguardar(() -> {
                JsonNode ticket = consultarTicket(ticketId);
                exigir(!"RECUSADO".equals(ticket.path("status").asText()), "Entrada recusada: " + ticketId);
                return "ATIVO".equals(ticket.path("status").asText());
            }, "Reserva do ticket " + ticketId);

            String vagaId = consultarTicket(ticketId).path("vagaId").asText();
            JsonNode vaga = http.consultar(vagas, "/vagas/" + vagaId);
            exigir("OCUPADA".equals(vaga.path("status").asText()) && ticketId.toString().equals(vaga.path("ticketId").asText()),
                "Vaga não foi ocupada pelo ticket " + ticketId);
            http.etapa("Depois da reserva do ticket " + ticketId);
            exigir(contarVagasLivres() == livresAntes - 1, "A entrada deve ocupar exatamente uma vaga");
            return vagaId;
        }

        private void sairEPagar(UUID ticketId, String metodo, HashSet<String> metodosRegistrados) throws InterruptedException {
            http.etapa("Antes da saída do ticket " + ticketId);
            String vagaId = consultarTicket(ticketId).path("vagaId").asText();
            exigir("OCUPADA".equals(http.consultar(vagas, "/vagas/" + vagaId).path("status").asText()),
                "A vaga deve estar ocupada antes da saída");

            http.etapa("Saída e cálculo do ticket " + ticketId);
            http.enviar(estacionamento, "/tickets/" + ticketId + "/saida", Map.of());
            http.aguardar(() -> statusDoTicket(ticketId, "AGUARDANDO_PAGAMENTO"), "Cálculo do ticket " + ticketId);

            http.etapa("Antes do pagamento do ticket " + ticketId);
            JsonNode calculado = http.consultar(pagamento, "/pagamentos/ticket/" + ticketId);
            exigir("CALCULADO".equals(calculado.path("status").asText()), "Pagamento não está calculado");
            exigirMesmoValor(calculado, consultarTicket(ticketId));

            http.etapa("Pagamento com " + metodo + " do ticket " + ticketId);
            JsonNode recebido = http.enviar(pagamento, "/pagamentos/ticket/" + ticketId + "/pagar", Map.of("metodo", metodo));
            exigir("PAGO".equals(recebido.path("status").asText()), "Pagamento não confirmado: " + ticketId);
            exigir(ticketId.toString().equals(recebido.path("ticketId").asText()), "Pagamento de outro ticket");
            String metodoId = recebido.path("metodoPagamentoId").asText();
            exigir(!metodoId.isBlank() && !"null".equals(metodoId) && metodosRegistrados.add(metodoId),
                "Os pagamentos devem usar métodos distintos");
            pagamentos.add(new PagamentoTeste(metodo, recebido));
            JsonNode pago = http.consultar(pagamento, "/pagamentos/ticket/" + ticketId);
            exigir("PAGO".equals(pago.path("status").asText()) && metodoId.equals(pago.path("metodoPagamentoId").asText()),
                "Pagamento não persistiu como esperado");

            http.etapa("Depois do pagamento do ticket " + ticketId);
            http.aguardar(() -> statusDoTicket(ticketId, "FINALIZADO"), "Finalização do ticket " + ticketId);
            http.aguardar(() -> "LIVRE".equals(http.consultar(vagas, "/vagas/" + vagaId).path("status").asText()),
                "Liberação da vaga " + vagaId);
            etapas.add("Ticket " + ticketId + " pago com " + metodo + ", finalizado e vaga liberada");
        }

        private void exigirMesmoValor(JsonNode calculado, JsonNode ticket) {
            exigir(!calculado.path("valor").isNull() && !ticket.path("valor").isNull()
                    && calculado.path("valor").decimalValue().compareTo(ticket.path("valor").decimalValue()) == 0,
                "Valor do pagamento diverge do valor do ticket");
        }

        private void exigirSemPagamento(UUID ticketId) {
            try {
                http.consultar(pagamento, "/pagamentos/ticket/" + ticketId);
            } catch (RestClientResponseException exception) {
                if (exception.getStatusCode().value() == 404) return;
                throw exception;
            }

            throw new IllegalStateException("Foi criado pagamento para o ticket recusado");
        }

        // ---- Consultas auxiliares ----

        private UUID registrarEntrada(String placa) {
            UUID ticketId = UUID.fromString(http.enviar(estacionamento, "/entrada", Map.of("placa", placa)).path("id").asText());
            tickets.add(ticketId);

            return ticketId;
        }

        private JsonNode consultarTicket(UUID ticketId) {
            return http.consultar(estacionamento, "/tickets/" + ticketId);
        }

        private boolean statusDoTicket(UUID ticketId, String status) {
            return status.equals(consultarTicket(ticketId).path("status").asText());
        }

        /** Cadastra no serviço vagas e confere que a consulta devolve o mesmo que a criação. */
        private JsonNode cadastrar(String rota, Map<String, ?> dados) {
            JsonNode criado = http.enviar(vagas, rota, dados);
            JsonNode consultado = http.consultar(vagas, rota + "/" + criado.path("id").asText());

            exigir(criado.equals(consultado), "Cadastro não persistiu como esperado: " + rota);

            return consultado;
        }

        /** Vagas livres em blocos ativos de setores ativos. */
        private int contarVagasLivres() {
            var setoresAtivos = new HashSet<String>();
            for (JsonNode setor : listarVagas("/setores")) {
                if ("ATIVO".equals(setor.path("status").asText())) setoresAtivos.add(setor.path("id").asText());
            }

            var blocosAtivos = new HashSet<String>();
            for (JsonNode bloco : listarVagas("/blocos")) {
                if ("ATIVO".equals(bloco.path("status").asText()) && setoresAtivos.contains(bloco.path("setorId").asText())) {
                    blocosAtivos.add(bloco.path("id").asText());
                }
            }

            return (int) listarVagas("/vagas").stream()
                .filter(vaga -> "LIVRE".equals(vaga.path("status").asText())
                    && blocosAtivos.contains(vaga.path("blocoId").asText()))
                .count();
        }

        /** Percorre todas as páginas de uma listagem do serviço vagas. */
        private List<JsonNode> listarVagas(String rota) {
            var itens = new ArrayList<JsonNode>();
            int pagina = 0;
            JsonNode resposta;

            do {
                resposta = http.consultar(vagas, rota + "?page=" + pagina++ + "&size=100");
                resposta.path("content").forEach(itens::add);
            } while (pagina < resposta.path("totalPages").asInt());

            return itens;
        }

        private String novaPlaca() {
            return UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        }

        private void exigir(boolean condicao, String mensagem) {
            if (!condicao) throw new IllegalStateException(mensagem);
        }
    }
}
