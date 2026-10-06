package br.furb.estacionamento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagem_processada")
public class MensagemProcessada {

    @Id
    @Column(name = "message_id", nullable = false)
    private UUID messageId;

    @Column(name = "processado_em", nullable = false)
    private Instant processadoEm;

    protected MensagemProcessada() {
    }

    public MensagemProcessada(UUID messageId, Instant processadoEm) {
        this.messageId = messageId;
        this.processadoEm = processadoEm;
    }
}