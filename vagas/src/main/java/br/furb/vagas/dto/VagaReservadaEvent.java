package br.furb.vagas.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public record VagaReservadaEvent(
        @JsonProperty("ticketId") UUID idTicket,
        @JsonProperty("vagaId") UUID idVaga,
        @JsonProperty("numeroVaga") String numero) {}
