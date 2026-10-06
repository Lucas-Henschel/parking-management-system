package br.furb.vagas.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagem_processada")
public class MensagemProcessada {
    @Id
    private UUID id;

    @Column(name = "processada_em", nullable = false)
    private Instant processadaEm;

    protected MensagemProcessada() {}

    public MensagemProcessada(UUID id, Instant processadaEm) {
        this.id = id;
        this.processadaEm = processadaEm;
    }

    public UUID obterId() { return id; }

    public Instant obterProcessadaEm() { return processadaEm; }
}
