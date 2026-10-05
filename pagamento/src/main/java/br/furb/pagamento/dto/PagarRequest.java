package br.furb.pagamento.dto;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

public record PagarRequest(
    @NotBlank 
    @Schema(description = "Método de pagamento (ex: PIX, DINHEIRO, CARTAO_CREDITO)", example = "PIX")
    String metodo
) {}
