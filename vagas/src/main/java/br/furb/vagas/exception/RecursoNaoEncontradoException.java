package br.furb.vagas.exception;

import br.furb.vagas.enums.Recurso;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(Recurso recurso) {
        super(recurso.descricao() + " não encontrado.");
    }
}
