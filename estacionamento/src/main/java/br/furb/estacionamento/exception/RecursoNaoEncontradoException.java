package br.furb.estacionamento.exception;

import br.furb.estacionamento.enums.Recurso;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(Recurso recurso) {
        super(recurso.descricao() + " não encontrado.");
    }
}
