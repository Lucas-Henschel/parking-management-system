package br.furb.estacionamento.exception;

public class ConflitoNegocioException extends RuntimeException {
    public ConflitoNegocioException(String mensagem) {
        super(mensagem);
    }
}
