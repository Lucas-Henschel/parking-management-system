package br.furb.pagamento.repository;

import br.furb.pagamento.entity.Pagamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByTicketId(UUID ticketId);
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Pagamento p WHERE p.ticketId = :ticketId")
    Optional<Pagamento> findByTicketIdForUpdate(@Param("ticketId") UUID ticketId);
}
