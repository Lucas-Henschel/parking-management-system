package br.furb.vagas.entity;

import br.furb.vagas.enums.VagaStatus;
import br.furb.vagas.exception.ConflitoNegocioException;
import jakarta.persistence.*;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "vaga")
public class Vaga {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 30)
    private String numero;

    @Column(name = "bloco_id", nullable = false)
    private UUID blocoId;

    @Column(name = "tipo_id", nullable = false)
    private UUID tipoId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VagaStatus status;

    @Column(name = "ticket_id")
    private UUID ticketId;

    protected Vaga() {}

    public Vaga(String numero, UUID blocoId, UUID tipoId) {
        this.numero = numero;
        this.blocoId = blocoId;
        this.tipoId = tipoId;
        this.status = VagaStatus.LIVRE;
    }

    public UUID obterId() { return id; }

    public String obterNumero() { return numero; }

    public UUID obterBlocoId() { return blocoId; }

    public UUID obterTipoId() { return tipoId; }

    public VagaStatus obterStatus() { return status; }

    public UUID obterTicketId() { return ticketId; }

    public void reservar(UUID ticketId) {
        if (status != VagaStatus.LIVRE) {
            throw new ConflitoNegocioException("A vaga não está livre.");
        }

        this.ticketId = Objects.requireNonNull(ticketId);
        this.status = VagaStatus.OCUPADA;
    }

    public void liberar(UUID ticketId) {
        if (status != VagaStatus.OCUPADA || !Objects.equals(this.ticketId, ticketId)) {
            throw new ConflitoNegocioException("A vaga não pertence ao ticket informado.");
        }

        this.ticketId = null;
        this.status = VagaStatus.LIVRE;
    }
}
