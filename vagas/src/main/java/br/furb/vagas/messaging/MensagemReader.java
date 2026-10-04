package br.furb.vagas.messaging;

import br.furb.vagas.dto.MensagemEnvelope;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MensagemReader {
    private final ObjectMapper conversor;
    public MensagemReader(ObjectMapper conversor) { this.conversor = conversor; }
    public MensagemEnvelope ler(byte[] corpo, String tipoEsperado) {
        try {
            MensagemEnvelope mensagem = conversor.readValue(corpo, MensagemEnvelope.class);
            if (mensagem.mensagemId() == null || mensagem.correlacaoId() == null || mensagem.instante() == null
                    || !tipoEsperado.equals(mensagem.tipo()) || mensagem.conteudo() == null
                    || !mensagem.conteudo().isObject())
                throw new IllegalArgumentException("Envelope inválido.");
            UUID ticketId = lerIdentificador(mensagem, "ticketId");
            if (!ticketId.equals(mensagem.correlacaoId()))
                throw new IllegalArgumentException("correlationId deve ser igual ao ticketId.");
            if ("LIBERAR_VAGA".equals(tipoEsperado)) lerIdentificador(mensagem, "vagaId");
            return mensagem;
        } catch (RuntimeException erro) {
            throw new AmqpRejectAndDontRequeueException("Mensagem inválida: " + tipoEsperado, erro);
        }
    }
    public UUID lerIdentificador(MensagemEnvelope mensagem, String campo) {
        var valor = mensagem.conteudo().get(campo);
        if (valor == null || !valor.isString()) throw new IllegalArgumentException(campo + " deve ser UUID.");
        String texto = valor.asString();
        UUID id = UUID.fromString(texto);
        if (!id.toString().equalsIgnoreCase(texto)) throw new IllegalArgumentException(campo + " deve ser UUID canônico.");
        return id;
    }
}
