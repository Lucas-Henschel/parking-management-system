package br.furb.vagas.dto;

import br.furb.vagas.entity.Bloco;
import br.furb.vagas.entity.CadastroStatus;
import java.util.UUID;

public record BlocoResponse(UUID id, UUID setorId, String codigo, CadastroStatus status) {
    public static BlocoResponse de(Bloco bloco) {
        return new BlocoResponse(bloco.obterId(), bloco.obterSetorId(), bloco.obterCodigo(), bloco.obterStatus());
    }
}
