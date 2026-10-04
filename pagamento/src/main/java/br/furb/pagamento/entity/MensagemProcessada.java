package br.furb.pagamento.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "mensagem_processada")
public class MensagemProcessada {
    @Id
    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "processado_em", nullable = false)
    private LocalDateTime processadoEm;

    public MensagemProcessada() {}
    public MensagemProcessada(UUID messageId, LocalDateTime processadoEm) {
        this.messageId = messageId;
        this.processadoEm = processadoEm;
    }
    public UUID getMessageId() { return messageId; }
    public void setMessageId(UUID messageId) { this.messageId = messageId; }
    public LocalDateTime getProcessadoEm() { return processadoEm; }
    public void setProcessadoEm(LocalDateTime processadoEm) { this.processadoEm = processadoEm; }
}
