package br.furb.vagas.messaging;

import br.furb.vagas.service.OcupacaoService;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class VagaListener {
    private final MensagemReader leitor;
    private final OcupacaoService ocupacao;
    public VagaListener(MensagemReader leitor, OcupacaoService ocupacao) {
        this.leitor = leitor; this.ocupacao = ocupacao;
    }
    @RabbitListener(queues = "vaga.reservar.queue")
    public void consumirReserva(Message mensagem) {
        var envelope = leitor.ler(mensagem.getBody(), "RESERVAR_VAGA");
        ocupacao.reservar(envelope, leitor.lerIdentificador(envelope, "ticketId"));
    }
    @RabbitListener(queues = "vaga.liberar.queue")
    public void consumirLiberacao(Message mensagem) {
        var envelope = leitor.ler(mensagem.getBody(), "LIBERAR_VAGA");
        ocupacao.liberar(envelope, leitor.lerIdentificador(envelope, "ticketId"),
                leitor.lerIdentificador(envelope, "vagaId"));
    }
}
