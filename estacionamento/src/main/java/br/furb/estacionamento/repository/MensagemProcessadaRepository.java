package br.furb.estacionamento.repository;

import br.furb.estacionamento.entity.MensagemProcessada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MensagemProcessadaRepository extends JpaRepository<MensagemProcessada, UUID> {
    @Modifying
    @Query(
        value = """
        INSERT INTO mensagem_processada (message_id, processado_em)
        VALUES (:messageId, :processadoEm)
        ON CONFLICT (message_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int registrarSeAusente(@Param("messageId") UUID messageId, @Param("processadoEm") Instant processadoEm);
}