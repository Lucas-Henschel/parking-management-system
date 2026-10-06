package br.furb.vagas.service;

import br.furb.vagas.entity.EventoPendente;
import br.furb.vagas.enums.ResultadoPublicacao;
import br.furb.vagas.messaging.VagaPublisher;
import br.furb.vagas.repository.EventoPendenteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PublicacaoOutboxServiceTest {
    private static final Duration BACKOFF_INICIAL = Duration.ofSeconds(1);
    private static final Duration BACKOFF_MAXIMO = Duration.ofSeconds(10);

    private final EventoPendenteRepository eventos = mock(EventoPendenteRepository.class);
    private final VagaPublisher publicador = mock(VagaPublisher.class);
    private final PublicacaoOutboxService servico = new PublicacaoOutboxService(
        eventos, publicador, BACKOFF_INICIAL.toMillis(), BACKOFF_MAXIMO.toMillis());
    private EventoPendente evento;

    @BeforeEach
    void preparar() {
        evento = new EventoPendente(UUID.randomUUID(), "vaga.reservada", "{}", Instant.now());
        when(eventos.buscarProximoParaPublicacao(any(Instant.class))).thenReturn(Optional.of(evento));
    }

    @Test
    void deveMarcarComoPublicadoSomenteAposPublicacao() {
        doAnswer(chamada -> {
            assertThat(evento.obterPublicadoEm()).isNull();
            return null;
        }).when(publicador).publicar(evento);

        assertThat(servico.publicarProximo()).isEqualTo(ResultadoPublicacao.PUBLICADO);

        verify(publicador).publicar(evento);
        assertThat(evento.obterPublicadoEm()).isNotNull();
        assertThat(evento.obterTentativas()).isZero();
    }

    @Test
    void deveInformarQuandoNaoHaEventoPendente() {
        when(eventos.buscarProximoParaPublicacao(any(Instant.class))).thenReturn(Optional.empty());

        assertThat(servico.publicarProximo()).isEqualTo(ResultadoPublicacao.SEM_EVENTO);
        verifyNoInteractions(publicador);
    }

    @Test
    void deveAdiarEventoQueFalhouSemPropagarAExcecao() {
        doThrow(new IllegalStateException("Broker indisponível")).when(publicador).publicar(evento);
        Instant antes = Instant.now();

        assertThat(servico.publicarProximo()).isEqualTo(ResultadoPublicacao.FALHOU);

        assertThat(evento.obterPublicadoEm()).isNull();
        assertThat(evento.obterTentativas()).isEqualTo(1);
        assertThat(evento.obterUltimoErro()).contains("IllegalStateException").contains("Broker indisponível");
        assertThat(evento.obterProximaTentativaEm()).isAfterOrEqualTo(antes.plus(BACKOFF_INICIAL));
    }

    @Test
    void deveDobrarAEsperaACadaFalhaAteOLimite() {
        doThrow(new IllegalStateException("Sem rota")).when(publicador).publicar(evento);

        Duration[] esperas = new Duration[6];
        for (int tentativa = 0; tentativa < esperas.length; tentativa++) {
            Instant antes = Instant.now();
            servico.publicarProximo();
            esperas[tentativa] = Duration.between(antes, evento.obterProximaTentativaEm());
        }

        assertThat(esperas[0]).isBetween(Duration.ofSeconds(1), Duration.ofMillis(1500));
        assertThat(esperas[1]).isBetween(Duration.ofSeconds(2), Duration.ofMillis(2500));
        assertThat(esperas[2]).isBetween(Duration.ofSeconds(4), Duration.ofMillis(4500));
        assertThat(esperas[3]).isBetween(Duration.ofSeconds(8), Duration.ofMillis(8500));
        assertThat(esperas[4]).isBetween(BACKOFF_MAXIMO, BACKOFF_MAXIMO.plusMillis(500));
        assertThat(esperas[5]).isBetween(BACKOFF_MAXIMO, BACKOFF_MAXIMO.plusMillis(500));
        assertThat(evento.obterTentativas()).isEqualTo(6);
    }
}
