package br.furb.vagas.dto;

import br.furb.vagas.entity.Setor;
import br.furb.vagas.enums.CadastroStatus;
import java.util.UUID;

public record SetorResponse(UUID id, String codigo, CadastroStatus status) {
    public static SetorResponse de(Setor setor) {
        return new SetorResponse(setor.obterId(), setor.obterCodigo(), setor.obterStatus());
    }
}
