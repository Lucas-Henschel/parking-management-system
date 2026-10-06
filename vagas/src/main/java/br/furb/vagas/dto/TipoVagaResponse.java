package br.furb.vagas.dto;

import br.furb.vagas.entity.TipoVaga;
import java.util.UUID;

public record TipoVagaResponse(UUID id, String nome) {
    public static TipoVagaResponse de(TipoVaga tipo) {
        return new TipoVagaResponse(tipo.obterId(), tipo.obterNome());
    }
}
