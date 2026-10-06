package br.furb.vagas.messaging;

import br.furb.vagas.enums.TipoMensagem;
import br.furb.vagas.dto.LiberarVagaRequest;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.ReservarVagaRequest;
import br.furb.vagas.service.LiberacaoVagaService;
import br.furb.vagas.service.ReservaVagaService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VagaListenerTest {
    private final ReservaVagaService reservaVagaService = mock(ReservaVagaService.class);
    private final LiberacaoVagaService liberacaoVagaService = mock(LiberacaoVagaService.class);
    private final VagaListener listener = new VagaListener(reservaVagaService, liberacaoVagaService);

    @Test
    void deveDelegarReservaAoService() {
        UUID ticketId = UUID.randomUUID();
        var envelope = new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.RESERVAR_VAGA, Instant.now(),
                new ReservarVagaRequest(ticketId));

        listener.receberReserva(envelope);

        verify(reservaVagaService).reservar(envelope);
    }

    @Test
    void deveDelegarLiberacaoAoService() {
        UUID ticketId = UUID.randomUUID();
        var envelope = new MensagemEnvelope<>(UUID.randomUUID(), ticketId, TipoMensagem.LIBERAR_VAGA, Instant.now(),
                new LiberarVagaRequest(ticketId, UUID.randomUUID()));

        listener.receberLiberacao(envelope);

        verify(liberacaoVagaService).liberar(envelope);
    }
}
