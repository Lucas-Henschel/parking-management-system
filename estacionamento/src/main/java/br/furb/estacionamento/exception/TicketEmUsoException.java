package br.furb.estacionamento.exception;

public class TicketEmUsoException extends ConflitoNegocioException {
    public TicketEmUsoException() {
        super("O veículo já possui um ticket em aberto.");
    }
}
