package br.furb.vagas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record MensagemEnvelope(
        @JsonProperty("messageId") UUID mensagemId,
        @JsonProperty("correlationId") UUID correlacaoId,
        String tipo,
        @JsonProperty("timestamp") @JsonFormat(shape = JsonFormat.Shape.STRING) Instant instante,
        @JsonProperty("payload") JsonNode conteudo) {}
