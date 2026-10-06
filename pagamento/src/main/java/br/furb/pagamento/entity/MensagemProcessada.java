package br.furb.pagamento.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagem_processada")
public class MensagemProcessada {
    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "processado_em", nullable = false)
    private Instant processadoEm;

    public MensagemProcessada() {}

    public MensagemProcessada(UUID messageId, Instant processadoEm) {
        this.messageId = messageId;
        this.processadoEm = processadoEm;
    }

    public UUID getMessageId() { return messageId; }

    public void setMessageId(UUID messageId) { this.messageId = messageId; }

    public Instant getProcessadoEm() { return processadoEm; }

    public void setProcessadoEm(Instant processadoEm) { this.processadoEm = processadoEm; }
}
