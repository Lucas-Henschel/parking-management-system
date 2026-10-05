package br.furb.pagamento.repository;

import br.furb.pagamento.entity.Pagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {
    Optional<Pagamento> findByTicketId(UUID ticketId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pagamento p WHERE p.ticketId = :ticketId")
    Optional<Pagamento> findByTicketIdForUpdate(@Param("ticketId") UUID ticketId);

    /**
     * Cria o pagamento CALCULADO de forma atômica. Retorna 1 se inseriu e 0 se o ticket já tinha
     * pagamento (UNIQUE em ticket_id), sem lançar exceção, para não abortar a transação.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO pagamento (id, ticket_id, valor, data, status)
            VALUES (:id, :ticketId, :valor, :data, 'CALCULADO')
            ON CONFLICT (ticket_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int inserirCalculadoSeAusente(
        @Param("id") UUID id,
        @Param("ticketId") UUID ticketId,
        @Param("valor") BigDecimal valor,
        @Param("data") Instant data
    );
}
