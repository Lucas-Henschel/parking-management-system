package br.furb.vagas.messaging;

import br.furb.vagas.config.RabbitMQConfig;
import br.furb.vagas.dto.LiberarVagaRequest;
import br.furb.vagas.dto.MensagemEnvelope;
import br.furb.vagas.dto.ReservarVagaRequest;
import br.furb.vagas.service.LiberacaoVagaService;
import br.furb.vagas.service.ReservaVagaService;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VagaListener {
    private final ReservaVagaService reservaVagaService;
    private final LiberacaoVagaService liberacaoVagaService;

    public VagaListener(ReservaVagaService reservaVagaService, LiberacaoVagaService liberacaoVagaService) {
        this.reservaVagaService = reservaVagaService;
        this.liberacaoVagaService = liberacaoVagaService;
    }

    @RabbitListener(queues = RabbitMQConfig.VAGA_RESERVAR_QUEUE)
    public void receberReserva(MensagemEnvelope<ReservarVagaRequest> envelope) {
        reservaVagaService.reservar(envelope);
    }

    @RabbitListener(queues = RabbitMQConfig.VAGA_LIBERAR_QUEUE)
    public void receberLiberacao(MensagemEnvelope<LiberarVagaRequest> envelope) {
        liberacaoVagaService.liberar(envelope);
    }
}
