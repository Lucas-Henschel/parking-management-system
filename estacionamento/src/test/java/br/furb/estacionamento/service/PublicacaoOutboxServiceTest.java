package br.furb.estacionamento.service;

import br.furb.estacionamento.entity.EventoPendente;
import br.furb.estacionamento.enums.ResultadoPublicacao;
import br.furb.estacionamento.messaging.TicketOutboxPublisher;
import br.furb.estacionamento.repository.EventoPendenteRepository;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicacaoOutboxServiceTest {
    private final EventoPendenteRepository eventos = mock(EventoPendenteRepository.class);
    private final TicketOutboxPublisher publicador = mock(TicketOutboxPublisher.class);
    private final PublicacaoOutboxService service = new PublicacaoOutboxService(eventos, publicador, 1000, 5000);

    private EventoPendente novoEvento() {
        EventoPendente evento = new EventoPendente(UUID.randomUUID(), "vaga.reservar", "{}", Instant.now());
        when(eventos.buscarProximoParaPublicacao(any())).thenReturn(Optional.of(evento));
        return evento;
    }

    @Test
    void semEventoPendenteNaoFazNada() {
        when(eventos.buscarProximoParaPublicacao(any())).thenReturn(Optional.empty());

        assertEquals(ResultadoPublicacao.SEM_EVENTO, service.publicarProximo());
    }

    @Test
    void publicacaoConfirmadaMarcaOEventoComoPublicado() {
        EventoPendente evento = novoEvento();

        assertEquals(ResultadoPublicacao.PUBLICADO, service.publicarProximo());
        assertNotNull(evento.obterPublicadoEm());
    }

    @Test
    void falhaAdiaOEventoComEsperaCrescenteELimitada() {
        EventoPendente evento = novoEvento();
        doThrow(new IllegalStateException("broker fora do ar")).when(publicador).publicar(evento);

        Instant antes = Instant.now();
        assertEquals(ResultadoPublicacao.FALHOU, service.publicarProximo());
        Duration primeira = Duration.between(antes, evento.obterProximaTentativa());

        service.publicarProximo();
        Duration segunda = Duration.between(antes, evento.obterProximaTentativa());

        for (int i = 0; i < 5; i++) {
            service.publicarProximo();
        }
        Duration limitada = Duration.between(antes, evento.obterProximaTentativa());

        assertEquals(7, evento.obterTentativas());
        assertNull(evento.obterPublicadoEm());
        assertEquals(true, primeira.toMillis() >= 1000 && primeira.toMillis() < 2000);
        assertEquals(true, segunda.toMillis() >= 2000 && segunda.toMillis() < 3000);
        assertEquals(true, limitada.toMillis() >= 5000 && limitada.toMillis() < 6000);
    }
}
