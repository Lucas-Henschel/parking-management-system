package br.furb.vagas.service;

import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.enums.TipoMensagem;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Valida o envelope recebido do Estacionamento. Dados inválidos lançam IllegalArgumentException,
 * que não é retentado e vai direto para a DLQ.
 */
@Component
public class MensagemValidador {
    public void validarEnvelope(MensagemEnvelope<?> envelope, TipoMensagem tipoEsperado) {
        if (envelope == null
            || envelope.messageId() == null
            || envelope.correlationId() == null
            || envelope.timestamp() == null
            || envelope.payload() == null
            || envelope.tipo() != tipoEsperado) {
            throw new IllegalArgumentException("Envelope inválido para " + tipoEsperado + ".");
        }
    }

    public void validarTicket(MensagemEnvelope<?> envelope, UUID ticketId) {
        if (ticketId == null) {
            throw new IllegalArgumentException("ticketId é obrigatório.");
        }

        if (!ticketId.equals(envelope.correlationId())) {
            throw new IllegalArgumentException("correlationId deve ser igual ao ticketId.");
        }
    }
}
