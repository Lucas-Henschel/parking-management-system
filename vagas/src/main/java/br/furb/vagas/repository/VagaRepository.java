package br.furb.vagas.repository;

import br.furb.vagas.entity.Vaga;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VagaRepository extends JpaRepository<Vaga, UUID> {
    /**
     * Escolhe a primeira vaga livre de setor e bloco ativos e a trava com SKIP LOCKED, para que
     * reservas concorrentes nunca disputem a mesma vaga.
     */
    @Query(
        value = """
            SELECT v.*
            FROM vaga v
            JOIN bloco b ON b.id = v.bloco_id
            JOIN setor s ON s.id = b.setor_id
            WHERE v.status = 'LIVRE'
              AND b.status = 'ATIVO'
              AND s.status = 'ATIVO'
            ORDER BY s.codigo_setor, b.codigo_bloco, v.numero, v.id
            LIMIT 1
            FOR UPDATE OF v SKIP LOCKED
        """,
        nativeQuery = true
    )
    Optional<Vaga> buscarLivreParaReserva();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Vaga v WHERE v.id = :id")
    Optional<Vaga> buscarParaAlteracao(@Param("id") UUID id);
}
