package br.furb.pagamento.entity;

import jakarta.persistence.*;

import java.util.UUID;

@Entity
@Table(name = "metodo_pagamento")
public class MetodoPagamento {

    @Id
    private UUID id;

    @Column(name = "nome_metodo", nullable = false, unique = true, length = 50)
    private String nomeMetodo;

    public UUID getId() {
        return id;
    }

    public String getNomeMetodo() {
        return nomeMetodo;
    }

    public void setNomeMetodo(String nomeMetodo) {
        this.nomeMetodo = nomeMetodo;
    }

    public void setId(UUID id) { this.id = id;}
}
