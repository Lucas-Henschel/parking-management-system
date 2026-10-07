package br.furb.pagamento.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "evento_pendente")
public class EventoPendente {
    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String rota;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String envelope;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "publicado_em")
    private Instant publicadoEm;

    @Column(nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa_em", nullable = false)
    private Instant proximaTentativaEm;

    @Column(name = "ultimo_erro", columnDefinition = "TEXT")
    private String ultimoErro;

    protected EventoPendente() {}

    public EventoPendente(UUID id, String rota, String envelope, Instant criadoEm) {
        this.id = id;
        this.rota = rota;
        this.envelope = envelope;
        this.criadoEm = criadoEm;
        this.proximaTentativaEm = criadoEm;
    }

    public UUID obterId() { return id; }

    public String obterRota() { return rota; }

    public String obterEnvelope() { return envelope; }

    public Instant obterCriadoEm() { return criadoEm; }

    public Instant obterPublicadoEm() { return publicadoEm; }

    public int obterTentativas() { return tentativas; }

    public Instant obterProximaTentativaEm() { return proximaTentativaEm; }

    public String obterUltimoErro() { return ultimoErro; }

    public void marcarComoPublicado(Instant publicadoEm) {
        this.publicadoEm = publicadoEm;
    }

    /**
     * Registra uma tentativa que falhou e adia o evento: ele só volta a ser candidato à
     * publicação em proximaTentativaEm, sem impedir que os demais eventos sejam publicados.
     */
    public void registrarFalha(Instant proximaTentativaEm, String erro) {
        this.tentativas++;
        this.proximaTentativaEm = proximaTentativaEm;
        this.ultimoErro = erro;
    }
}
