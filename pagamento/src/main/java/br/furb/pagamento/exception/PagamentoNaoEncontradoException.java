package br.furb.pagamento.exception;

import java.util.UUID;

public class PagamentoNaoEncontradoException extends RuntimeException {

    public PagamentoNaoEncontradoException(UUID id) {
        super("Pagamento não encontrado: " + id);
    }
}
