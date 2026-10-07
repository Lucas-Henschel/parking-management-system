package br.furb.estacionamento.dto;

import java.util.List;
import java.util.UUID;
import java.time.Instant;
import tools.jackson.databind.JsonNode;

public record TesteResponse(
    boolean sucesso,
    String execucao,
    List<String> etapas,
    List<UUID> tickets,
    List<PagamentoTeste> pagamentos,
    List<Evidencia> evidencias,
    String erro,
    String observacao
) {
    public record PagamentoTeste(String metodo, JsonNode pagamento) {}

    public record Evidencia(
        Instant instante,
        String etapa,
        String servico,
        String metodo,
        String rota,
        Integer statusHttp,
        Object requisicao,
        Object resultado
    ) {}
}
