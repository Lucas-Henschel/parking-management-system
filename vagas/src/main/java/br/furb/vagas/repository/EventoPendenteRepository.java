package br.furb.vagas.repository;

import br.furb.vagas.entity.EventoPendente;
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
            INSERT INTO evento_pendente (id, rota, envelope, criado_em, proxima_tentativa_em)
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

    /**
     * Busca o evento pendente mais antigo cuja próxima tentativa já venceu e o trava com SKIP
     * LOCKED, permitindo várias instâncias publicando sem pegar o mesmo evento. Eventos que
     * falharam ficam fora da busca até o fim do backoff, então não travam os seguintes.
     */
    @Query(
        value = """
            SELECT *
            FROM evento_pendente
            WHERE publicado_em IS NULL
              AND proxima_tentativa_em <= :agora
            ORDER BY criado_em, id
            LIMIT 1
            FOR UPDATE SKIP LOCKED
        """,
        nativeQuery = true
    )
    Optional<EventoPendente> buscarProximoParaPublicacao(@Param("agora") Instant agora);
}
