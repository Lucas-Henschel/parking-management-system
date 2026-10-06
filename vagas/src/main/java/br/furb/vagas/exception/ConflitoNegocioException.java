package br.furb.vagas.exception;

public class ConflitoNegocioException extends RuntimeException {
    public ConflitoNegocioException(String mensagem) { super(mensagem); }
}
