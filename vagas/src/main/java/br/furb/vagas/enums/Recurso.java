package br.furb.vagas.enums;

public enum Recurso {
    SETOR("Setor"),
    BLOCO("Bloco"),
    TIPO_VAGA("Tipo de vaga"),
    VAGA("Vaga");

    private final String descricao;

    Recurso(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() { return descricao; }
}
