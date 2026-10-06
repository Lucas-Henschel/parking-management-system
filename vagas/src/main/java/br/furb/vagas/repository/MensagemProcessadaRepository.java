package br.furb.vagas.repository;

import br.furb.vagas.entity.MensagemProcessada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface MensagemProcessadaRepository extends JpaRepository<MensagemProcessada, UUID> {
    /**
     * Registra o messageId de forma atômica. Retorna 1 se foi registrado agora e 0 se já existia
     * (mensagem duplicada), sem lançar exceção, para não abortar a transação.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO mensagem_processada (id, processada_em)
            VALUES (:id, :processadaEm)
            ON CONFLICT (id) DO NOTHING
        """,
        nativeQuery = true
    )
    int registrarSeAusente(
        @Param("id") UUID id,
        @Param("processadaEm") Instant processadaEm
    );
}
