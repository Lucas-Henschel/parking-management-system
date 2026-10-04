package br.furb.vagas.service;

import br.furb.vagas.messaging.VagaPublisher;
import br.furb.vagas.repository.OutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicacaoOutboxService {
    private final OutboxRepository eventos;
    private final VagaPublisher publicador;
    public PublicacaoOutboxService(OutboxRepository eventos, VagaPublisher publicador) {
        this.eventos = eventos; this.publicador = publicador;
    }
    @Transactional
    public boolean publicarProximo() {
        var proximo = eventos.buscarParaPublicacao();
        if (proximo.isEmpty()) return false;
        var evento = proximo.get();
        publicador.publicar(evento);
        eventos.confirmarPublicacao(evento.id());
        return true;
    }
}
