package br.furb.estacionamento.service;

import br.furb.estacionamento.messaging.TicketOutboxPublisher;
import br.furb.estacionamento.repository.EventoPendenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PublicacaoOutboxService {
    private static final Logger log = LoggerFactory.getLogger(PublicacaoOutboxService.class);
    private final EventoPendenteRepository eventos;
    private final TicketOutboxPublisher publicador;
    private final long intervalo;

    public PublicacaoOutboxService(EventoPendenteRepository eventos, TicketOutboxPublisher publicador,
            @Value("${estacionamento.outbox.intervalo-ms:1000}") long intervalo) {
        if (intervalo <= 0) {
            throw new IllegalArgumentException("O intervalo de publicação deve ser positivo");
        }
        this.eventos = eventos;
        this.publicador = publicador;
        this.intervalo = intervalo;
    }

    @Transactional
    public boolean publicarProximo() {
        var proximo = eventos.buscarProximoParaPublicacao(Instant.now());
        if (proximo.isEmpty()) {
            return false;
        }
        var evento = proximo.get();
        try {
            publicador.publicar(evento);
        } catch (RuntimeException erro) {
            evento.registrarFalha(Instant.now().plusMillis(intervalo), erro.toString());
            log.warn("Falha ao publicar evento {}; será reenviado: {}", evento.obterId(), erro.getMessage());
            return true;
        }
        evento.marcarComoPublicado(Instant.now());
        return true;
    }
}
