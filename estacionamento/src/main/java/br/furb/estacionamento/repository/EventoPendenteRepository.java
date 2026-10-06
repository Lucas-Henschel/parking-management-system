package br.furb.estacionamento.repository;

import br.furb.estacionamento.entity.EventoPendente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EventoPendenteRepository extends JpaRepository<EventoPendente, UUID> {
    @Modifying
    @Query(
        value = """
            INSERT INTO evento_pendente (id, rota, envelope, criado_em, proxima_tentativa)
            VALUES (:id, :rota, :envelope, :criadoEm, :criadoEm)
            """,
        nativeQuery = true
    )
    int registrar(
        @Param("id") UUID id,
        @Param("rota") String rota,
        @Param("envelope") String envelope,
        @Param("criadoEm") Instant criadoEm
    );

    @Query(
        value = """
            SELECT * FROM evento_pendente
            WHERE publicado_em IS NULL AND proxima_tentativa <= :agora
            ORDER BY proxima_tentativa, criado_em, id
            LIMIT 1 FOR UPDATE SKIP LOCKED
            """,
        nativeQuery = true
    )
    Optional<EventoPendente> buscarProximoParaPublicacao(@Param("agora") Instant agora);
}
