package br.furb.estacionamento.entity;

import br.furb.estacionamento.enums.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ticket")
public class Ticket {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "veiculo_id", nullable = false)
    private Veiculo veiculo;

    @Column(name = "vaga_id")
    private UUID vagaId;

    @Column(nullable = false)
    private Instant entrada;

    private Instant saida;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TicketStatus status;

    @Column(precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(name = "pagamento_id")
    private UUID pagamentoId;

    @Column(name = "valor_confirmado", precision = 10, scale = 2)
    private BigDecimal valorConfirmado;

    @Column(name = "tentativas_reserva", nullable = false)
    private int tentativasReserva = 1;

    protected Ticket() {
    }

    public Ticket(Veiculo veiculo, Instant entrada) {
        this.veiculo = veiculo;
        this.entrada = entrada;
        this.status = TicketStatus.PENDENTE;
    }

    public UUID getId() { return id; }
    public Veiculo getVeiculo() { return veiculo; }
    public UUID getVagaId() { return vagaId; }
    public Instant getEntrada() { return entrada; }
    public Instant getSaida() { return saida; }
    public TicketStatus getStatus() { return status; }
    public BigDecimal getValor() { return valor; }
    public UUID getPagamentoId() { return pagamentoId; }
    public BigDecimal getValorConfirmado() { return valorConfirmado; }
    public int getTentativasReserva() { return tentativasReserva; }

    public void confirmarVaga(UUID vagaId) {
        this.vagaId = vagaId;
        this.status = TicketStatus.ATIVO;
    }

    public void recusar(Instant saida) {
        this.status = TicketStatus.RECUSADO;
        this.saida = saida;
    }

    public void registrarNovaTentativaReserva() {
        tentativasReserva++;
    }

    public void registrarSaida(Instant saida) {
        this.saida = saida;
    }

    public void registrarCalculo(UUID pagamentoId, BigDecimal valor) {
        this.pagamentoId = pagamentoId;
        this.valor = valor;
        this.status = TicketStatus.AGUARDANDO_PAGAMENTO;
    }

    public void registrarConfirmacaoAntecipada(UUID pagamentoId, BigDecimal valorConfirmado) {
        this.pagamentoId = pagamentoId;
        this.valorConfirmado = valorConfirmado;
    }

    public void vincularPagamento(UUID pagamentoId) {
        this.pagamentoId = pagamentoId;
    }

    public void finalizar(BigDecimal valorConfirmado) {
        this.valorConfirmado = valorConfirmado;
        this.status = TicketStatus.FINALIZADO;
    }
}
