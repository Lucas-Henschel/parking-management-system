package br.furb.vagas.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "setor")
public class Setor {
    @Id private UUID id;
    @Column(name = "codigo_setor", nullable = false, length = 30) private String codigo;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private CadastroStatus status;
    protected Setor() {}
    public Setor(String codigo, CadastroStatus status) { this.id = UUID.randomUUID(); this.codigo = codigo; this.status = status; }
    public UUID obterId() { return id; }
    public String obterCodigo() { return codigo; }
    public CadastroStatus obterStatus() { return status; }
    public void atualizar(String codigo, CadastroStatus status) { this.codigo = codigo; this.status = status; }
}
