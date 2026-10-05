package br.furb.vagas.entity;

import br.furb.vagas.enums.CadastroStatus;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "bloco")
public class Bloco {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "setor_id", nullable = false)
    private UUID setorId;

    @Column(name = "codigo_bloco", nullable = false, length = 30)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CadastroStatus status;

    protected Bloco() {}

    public Bloco(UUID setorId, String codigo, CadastroStatus status) {
        this.setorId = setorId;
        this.codigo = codigo;
        this.status = status;
    }

    public UUID obterId() { return id; }

    public UUID obterSetorId() { return setorId; }

    public String obterCodigo() { return codigo; }

    public CadastroStatus obterStatus() { return status; }
}
