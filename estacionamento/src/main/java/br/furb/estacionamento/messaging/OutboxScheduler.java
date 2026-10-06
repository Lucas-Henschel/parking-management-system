package br.furb.estacionamento.messaging;

import br.furb.estacionamento.enums.ResultadoPublicacao;
import br.furb.estacionamento.service.PublicacaoOutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "estacionamento.outbox.habilitada", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private static final int LIMITE_EVENTOS_POR_EXECUCAO = 50;
    private static final int LIMITE_FALHAS_POR_EXECUCAO = 3;

    private final PublicacaoOutboxService publicacao;

    public OutboxScheduler(PublicacaoOutboxService publicacao) {
        this.publicacao = publicacao;
    }

    /**
     * Uma falha adia apenas o evento que falhou, então a execução continua com os próximos. Para
     * não ficar martelando um broker fora do ar, ela para após algumas falhas seguidas.
     */
    @Scheduled(fixedDelayString = "${estacionamento.outbox.intervalo-ms:1000}")
    public void publicarPendentes() {
        try {
            int processados = 0;
            int falhas = 0;

            while (processados < LIMITE_EVENTOS_POR_EXECUCAO && falhas < LIMITE_FALHAS_POR_EXECUCAO) {
                ResultadoPublicacao resultado = publicacao.publicarProximo();

                if (resultado == ResultadoPublicacao.SEM_EVENTO) {
                    return;
                }

                processados++;

                if (resultado == ResultadoPublicacao.FALHOU) {
                    falhas++;
                }
            }
        } catch (RuntimeException erro) {
            log.warn("Erro ao processar a outbox; os eventos pendentes serão tentados na próxima execução.", erro);
        }
    }
}
