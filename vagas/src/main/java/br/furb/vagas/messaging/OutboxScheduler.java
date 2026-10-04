package br.furb.vagas.messaging;

import br.furb.vagas.service.PublicacaoOutboxService;
import org.slf4j.*;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "vagas.outbox.habilitada", havingValue = "true", matchIfMissing = true)
@Component
@EnableScheduling
public class OutboxScheduler {
    private static final Logger REGISTRO = LoggerFactory.getLogger(OutboxScheduler.class);
    private final PublicacaoOutboxService publicacao;
    public OutboxScheduler(PublicacaoOutboxService publicacao) { this.publicacao = publicacao; }
    @Scheduled(fixedDelayString = "${vagas.outbox.intervalo-ms:1000}")
    public void publicarPendentes() {
        try {
            for (int quantidade = 0; quantidade < 50 && publicacao.publicarProximo(); quantidade++) {}
        } catch (RuntimeException erro) {
            REGISTRO.warn("Evento permanece na outbox para nova tentativa de publicação.", erro);
        }
    }
}
