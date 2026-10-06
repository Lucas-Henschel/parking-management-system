package br.furb.estacionamento.service;

import br.furb.estacionamento.dto.MensagemEnvelope;
import br.furb.estacionamento.enums.TipoMensagem;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.UUID;

/**
 * Valida o envelope recebido dos outros serviços. Dados inválidos lançam
 * IllegalArgumentException, que não é retentado e vai direto para a DLQ.
 */
@Component
public class MensagemValidador {
    public void validarEnvelope(MensagemEnvelope<?> envelope, TipoMensagem... tiposAceitos) {
        if (envelope == null
            || envelope.messageId() == null
            || envelope.correlationId() == null
            || envelope.timestamp() == null
            || envelope.payload() == null
            || envelope.tipo() == null
            || !Arrays.asList(tiposAceitos).contains(envelope.tipo())) {
            throw new IllegalArgumentException("Envelope inválido para " + Arrays.toString(tiposAceitos) + ".");
        }
    }

    public void validarTicket(MensagemEnvelope<?> envelope, UUID ticketId) {
        validarIdentificador(ticketId, "ticketId");

        if (!ticketId.equals(envelope.correlationId())) {
            throw new IllegalArgumentException("correlationId deve corresponder ao ticketId.");
        }
    }

    public void validarIdentificador(UUID id, String campo) {
        if (id == null) {
            throw new IllegalArgumentException(campo + " é obrigatório.");
        }
    }
}
