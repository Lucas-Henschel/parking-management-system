package br.furb.estacionamento.enums;

public enum Recurso {
    TICKET("Ticket");

    private final String descricao;

    Recurso(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
