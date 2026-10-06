package br.furb.estacionamento.service;

import br.furb.estacionamento.entity.EventoPendente;
import br.furb.estacionamento.enums.ResultadoPublicacao;
import br.furb.estacionamento.messaging.TicketOutboxPublisher;
import br.furb.estacionamento.repository.EventoPendenteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class PublicacaoOutboxService {
    private static final Logger log = LoggerFactory.getLogger(PublicacaoOutboxService.class);
    private static final int LIMITE_EXPOENTE = 20;
    private static final int TAMANHO_MAXIMO_ERRO = 1000;

    private final EventoPendenteRepository eventos;
    private final TicketOutboxPublisher publicador;
    private final Duration backoffInicial;
    private final Duration backoffMaximo;

    public PublicacaoOutboxService(
        EventoPendenteRepository eventos,
        TicketOutboxPublisher publicador,
        @Value("${estacionamento.outbox.backoff-inicial-ms:1000}") long backoffInicialMs,
        @Value("${estacionamento.outbox.backoff-maximo-ms:300000}") long backoffMaximoMs
    ) {
        this.eventos = eventos;
        this.publicador = publicador;
        this.backoffInicial = Duration.ofMillis(backoffInicialMs);
        this.backoffMaximo = Duration.ofMillis(backoffMaximoMs);
    }

    /**
     * Publica o próximo evento pendente e só o marca como publicado se o broker confirmar.
     * Se a publicação falhar, o evento é adiado com backoff exponencial e a falha é gravada na
     * mesma transação, para que ele não bloqueie os eventos seguintes.
     */
    @Transactional
    public ResultadoPublicacao publicarProximo() {
        Instant agora = Instant.now();
        Optional<EventoPendente> proximo = eventos.buscarProximoParaPublicacao(agora);

        if (proximo.isEmpty()) {
            return ResultadoPublicacao.SEM_EVENTO;
        }

        EventoPendente evento = proximo.get();

        try {
            publicador.publicar(evento);
        } catch (RuntimeException erro) {
            Instant proximaTentativa = agora.plus(calcularEspera(evento.obterTentativas() + 1));

            evento.registrarFalha(proximaTentativa, resumir(erro));
            log.warn(
                "Falha ao publicar o evento {} (tentativa {}); nova tentativa em {}: {}",
                evento.obterId(), evento.obterTentativas(), proximaTentativa, erro.getMessage()
            );

            return ResultadoPublicacao.FALHOU;
        }

        evento.marcarComoPublicado(Instant.now());

        return ResultadoPublicacao.PUBLICADO;
    }

    private Duration calcularEspera(int tentativa) {
        long fator = 1L << Math.min(tentativa - 1, LIMITE_EXPOENTE);
        Duration espera = backoffInicial.multipliedBy(fator);

        return espera.compareTo(backoffMaximo) > 0 ? backoffMaximo : espera;
    }

    private String resumir(RuntimeException erro) {
        String texto = erro.getClass().getSimpleName() + ": " + erro.getMessage();

        return texto.length() > TAMANHO_MAXIMO_ERRO ? texto.substring(0, TAMANHO_MAXIMO_ERRO) : texto;
    }
}
