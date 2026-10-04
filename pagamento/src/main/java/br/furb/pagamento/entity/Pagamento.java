package br.furb.pagamento.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "pagamento")
public class Pagamento {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false, unique = true)
    private UUID ticketId;

    @Column(name = "metodo_pagamento_id")
    private UUID metodoPagamentoId;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal valor;

    @Column(nullable = false)
    private LocalDateTime data;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PagamentoStatus status;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getTicketId() { return ticketId; }
    public void setTicketId(UUID ticketId) { this.ticketId = ticketId; }
    public UUID getMetodoPagamentoId() { return metodoPagamentoId; }
    public void setMetodoPagamentoId(UUID metodoPagamentoId) { this.metodoPagamentoId = metodoPagamentoId; }
    public BigDecimal getValor() { return valor; }
    public void setValor(BigDecimal valor) { this.valor = valor; }
    public LocalDateTime getData() { return data; }
    public void setData(LocalDateTime data) { this.data = data; }
    public PagamentoStatus getStatus() { return status; }
    public void setStatus(PagamentoStatus status) { this.status = status; }
}
