package br.furb.estacionamento.exception;

public class EstadoInvalidoException extends ConflitoNegocioException {
    public EstadoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
