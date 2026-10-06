package br.furb.estacionamento.messaging;

import br.furb.estacionamento.service.PublicacaoOutboxService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "estacionamento.outbox.habilitada", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final PublicacaoOutboxService publicacao;

    public OutboxScheduler(PublicacaoOutboxService publicacao) {
        this.publicacao = publicacao;
    }

    @Scheduled(fixedDelayString = "${estacionamento.outbox.intervalo-ms:1000}")
    public void publicarPendentes() {
        for (int i = 0; i < 100 && publicacao.publicarProximo(); i++) {
            // Cada evento é publicado em sua própria transação, com lock entre instâncias.
        }
    }
}
