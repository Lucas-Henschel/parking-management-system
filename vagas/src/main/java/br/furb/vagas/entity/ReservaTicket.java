package br.furb.vagas.entity;

import br.furb.vagas.enums.ReservaSituacao;
import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "reserva_ticket")
public class ReservaTicket {
    @Id
    @Column(name = "ticket_id")
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReservaSituacao situacao;

    @Column(name = "vaga_id")
    private UUID vagaId;

    @Column(name = "numero_vaga", length = 30)
    private String numeroVaga;

    protected ReservaTicket() {}

    public ReservaTicket(UUID ticketId) {
        this.ticketId = ticketId;
        this.situacao = ReservaSituacao.PENDENTE;
    }

    public UUID obterTicketId() { return ticketId; }

    public ReservaSituacao obterSituacao() { return situacao; }

    public UUID obterVagaId() { return vagaId; }

    public String obterNumeroVaga() { return numeroVaga; }

    public void registrarReserva(UUID vagaId, String numeroVaga) {
        this.situacao = ReservaSituacao.RESERVADA;
        this.vagaId = vagaId;
        this.numeroVaga = numeroVaga;
    }

    public void registrarIndisponibilidade() {
        this.situacao = ReservaSituacao.INDISPONIVEL;
        this.vagaId = null;
        this.numeroVaga = null;
    }

    /**
     * Encerra o ticket: impede nova reserva e mantém, se houver, a vaga que ele ocupou.
     */
    public void encerrar() {
        this.situacao = ReservaSituacao.LIBERADA;
    }
}
