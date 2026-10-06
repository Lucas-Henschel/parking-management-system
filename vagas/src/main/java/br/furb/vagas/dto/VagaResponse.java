package br.furb.vagas.dto;

import br.furb.vagas.enums.VagaStatus;
import br.furb.vagas.entity.Vaga;
import java.util.UUID;

public record VagaResponse(
    UUID id,
    String numero,
    UUID blocoId,
    UUID tipoId,
    VagaStatus status,
    UUID ticketId
) {
    public static VagaResponse de(Vaga vaga) {
        return new VagaResponse(
            vaga.obterId(),
            vaga.obterNumero(),
            vaga.obterBlocoId(),
            vaga.obterTipoId(),
            vaga.obterStatus(),
            vaga.obterTicketId()
        );
    }
}
