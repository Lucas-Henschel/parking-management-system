package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.TesteResponse.Evidencia;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * Chamadas HTTP de uma execução da simulação: envia as requisições, registra as evidências
 * (e os logs) e espera os resultados assíncronos do RabbitMQ.
 */
class SimulacaoHttp {
    private static final Logger log = LoggerFactory.getLogger(SimulacaoHttp.class);
    private static final long INTERVALO_CONSULTA_MS = 250;

    private final String execucao;
    private final RestClient estacionamento;
    private final RestClient vagas;
    private final RestClient pagamento;
    private final int esperaSegundos;
    private final List<Evidencia> evidencias = new ArrayList<>();
    private String etapa;
    private boolean esperando;

    SimulacaoHttp(
        String execucao,
        RestClient estacionamento,
        RestClient vagas,
        RestClient pagamento,
        int esperaSegundos,
        String etapaInicial
    ) {
        this.execucao = execucao;
        this.estacionamento = estacionamento;
        this.vagas = vagas;
        this.pagamento = pagamento;
        this.esperaSegundos = esperaSegundos;
        this.etapa = etapaInicial;
    }

    void etapa(String etapa) {
        this.etapa = etapa;
    }

    List<Evidencia> evidencias() {
        return evidencias;
    }

    JsonNode consultar(RestClient cliente, String rota) {
        return chamar(cliente, "GET", rota, null);
    }

    JsonNode enviar(RestClient cliente, String rota, Map<String, ?> dados) {
        return chamar(cliente, "POST", rota, dados);
    }

    /** Repete a consulta a cada 250 ms até confirmar ou esgotar {@code teste.espera-segundos}. */
    void aguardar(BooleanSupplier concluido, String descricao) throws InterruptedException {
        long inicio = System.nanoTime();
        long limite = inicio + Duration.ofSeconds(esperaSegundos).toNanos();
        int consultas = 0;

        boolean confirmou = false;
        esperando = true;

        try {
            do {
                consultas++;
                confirmou = concluido.getAsBoolean();

                if (!confirmou) {
                    log.info("[teste {}] Aguardando {}: consulta {} ainda não confirmou o resultado", execucao, descricao, consultas);

                    if (System.nanoTime() >= limite) {
                        throw new IllegalStateException("Tempo esgotado: " + descricao + ". Confira os consumidores e o RabbitMQ.");
                    }

                    Thread.sleep(INTERVALO_CONSULTA_MS);
                }
            } while (!confirmou);
        } finally {
            esperando = false;
            registrar("RabbitMQ", "ESPERA", descricao, null, null, Map.of(
                "consultas", consultas,
                "duracaoMs", Duration.ofNanos(System.nanoTime() - inicio).toMillis(),
                "concluida", confirmou));
        }
    }

    private JsonNode chamar(RestClient cliente, String metodo, String rota, Map<String, ?> dados) {
        String servico = nomeDoServico(cliente);
        log.info("[teste {}] {} | {} {} {} | envio={}", execucao, etapa, servico, metodo, rota, dados);

        try {
            var resposta = dados == null
                ? cliente.get().uri(rota).retrieve().toEntity(JsonNode.class)
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

    private String nomeDoServico(RestClient cliente) {
        if (cliente == estacionamento) return "estacionamento";
        if (cliente == vagas) return "vagas";
        if (cliente == pagamento) return "pagamento";

        return "desconhecido";
    }

    /** As consultas repetidas de uma espera bem-sucedida ficam só nos logs; o resumo vai em "ESPERA". */
    private void registrar(String servico, String metodo, String rota, Integer status, Object dados, Object resultado) {
        boolean consultaDeEspera = esperando && status != null && status < 400;

        if (!consultaDeEspera) {
            evidencias.add(new Evidencia(Instant.now(), etapa, servico, metodo, rota, status, dados, resultado));
        }

        log.info("[teste {}] {} | {} {} {} | HTTP={} | resultado={}", execucao, etapa, servico, metodo, rota, status, resultado);
    }
}
