package br.furb.vagas.repository;

import br.furb.vagas.entity.Vaga;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface VagaRepository extends JpaRepository<Vaga, UUID> {
    @Query(value = """
        SELECT v.* FROM vaga v
        JOIN bloco b ON b.id = v.bloco_id JOIN setor s ON s.id = b.setor_id
        WHERE v.status = 'LIVRE' AND b.status = 'ATIVO' AND s.status = 'ATIVO'
        ORDER BY s.codigo_setor, b.codigo_bloco, v.numero, v.id
        LIMIT 1 FOR UPDATE OF v SKIP LOCKED
        """, nativeQuery = true)
    Optional<Vaga> buscarLivreParaReserva();

    @Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vaga v where v.id = :id")
    Optional<Vaga> buscarParaAlteracao(@Param("id") UUID id);

    @Query(value = """
        SELECT count(*) FROM vaga v JOIN bloco b ON b.id = v.bloco_id
        JOIN setor s ON s.id = b.setor_id
        WHERE v.status = 'LIVRE' AND b.status = 'ATIVO' AND s.status = 'ATIVO'
        """, nativeQuery = true)
    long contarDisponiveis();
    @Query("select count(v) from Vaga v where v.status = :status")
    long contarPorStatus(@Param("status") br.furb.vagas.entity.VagaStatus status);

}
