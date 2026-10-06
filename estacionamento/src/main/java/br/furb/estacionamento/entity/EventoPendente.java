package br.furb.estacionamento.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_pendente")
public class EventoPendente {
    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String rota;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String envelope;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa", nullable = false)
    private Instant proximaTentativa;

    @Column(name = "ultimo_erro", length = 1000)
    private String ultimoErro;

    protected EventoPendente() {}

    public EventoPendente(UUID id, String rota, String envelope, Instant criadoEm) {
        this.id = id;
        this.rota = rota;
        this.envelope = envelope;
        this.criadoEm = criadoEm;
        this.proximaTentativa = criadoEm;
    }

    public UUID obterId() { return id; }
    public String obterRota() { return rota; }
    public String obterEnvelope() { return envelope; }

    public void marcarComoPublicado(Instant agora) {
        publicadoEm = agora;
        ultimoErro = null;
    }

    public void registrarFalha(Instant proximaTentativa, String erro) {
        tentativas++;
        this.proximaTentativa = proximaTentativa;
        ultimoErro = erro.substring(0, Math.min(erro.length(), 1000));
    }
}
