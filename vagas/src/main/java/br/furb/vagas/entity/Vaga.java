package br.furb.vagas.entity;

import br.furb.vagas.exception.ConflitoNegocioException;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "vaga")
public class Vaga {
    @Id private UUID id;
    @Column(nullable = false, length = 30) private String numero;
    @Column(name = "bloco_id", nullable = false) private UUID blocoId;
    @Column(name = "tipo_id", nullable = false) private UUID tipoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private VagaStatus status;
    @Column(name = "ticket_id") private UUID ticketId;
    protected Vaga() {}
    public Vaga(String numero, UUID blocoId, UUID tipoId) { this.id = UUID.randomUUID(); this.numero = numero; this.blocoId = blocoId; this.tipoId = tipoId; this.status = VagaStatus.LIVRE; }
    public UUID obterId() { return id; }
    public String obterNumero() { return numero; }
    public UUID obterBlocoId() { return blocoId; }
    public UUID obterTipoId() { return tipoId; }
    public VagaStatus obterStatus() { return status; }
    public UUID obterTicketId() { return ticketId; }
    public void atualizar(String numero, UUID blocoId, UUID tipoId) {
        if (status == VagaStatus.OCUPADA) throw new ConflitoNegocioException("Uma vaga ocupada não pode ser alterada.");
        this.numero = numero; this.blocoId = blocoId; this.tipoId = tipoId;
    }
    public void alterarBloqueio(boolean bloqueada) {
        if (status == VagaStatus.OCUPADA) throw new ConflitoNegocioException("Uma vaga ocupada não pode ser bloqueada.");
        status = bloqueada ? VagaStatus.BLOQUEADA : VagaStatus.LIVRE;
    }
    public void reservar(UUID ticketId) {
        if (status != VagaStatus.LIVRE) throw new ConflitoNegocioException("A vaga não está livre.");
        this.ticketId = java.util.Objects.requireNonNull(ticketId); status = VagaStatus.OCUPADA;
    }
    public void liberar(UUID ticketId) {
        if (status != VagaStatus.OCUPADA || !java.util.Objects.equals(this.ticketId, ticketId))
            throw new ConflitoNegocioException("A vaga não pertence ao ticket informado.");
        this.ticketId = null; status = VagaStatus.LIVRE;
    }
}
