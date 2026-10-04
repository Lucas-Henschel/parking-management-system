package br.furb.vagas.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "tipo_vaga")
public class TipoVaga {
    @Id private UUID id;
    @Column(name = "nome_tipo", nullable = false, length = 80) private String nome;
    protected TipoVaga() {}
    public TipoVaga(String nome) { this.id = UUID.randomUUID(); this.nome = nome; }
    public UUID obterId() { return id; }
    public String obterNome() { return nome; }
    public void atualizar(String nome) { this.nome = nome; }
}
