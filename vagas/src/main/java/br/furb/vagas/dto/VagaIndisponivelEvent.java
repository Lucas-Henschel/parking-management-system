package br.furb.vagas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record VagaIndisponivelEvent(
        @JsonProperty("ticketId") UUID idTicket,
        String motivo) {}
