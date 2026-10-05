package br.furb.vagas.repository;

import br.furb.vagas.entity.ReservaTicket;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReservaTicketRepository extends JpaRepository<ReservaTicket, UUID> {
    /**
     * Cria a reserva PENDENTE do ticket de forma atômica. Retorna 1 se criou e 0 se o ticket já
     * tinha reserva, sem lançar exceção, para não abortar a transação.
     */
    @Modifying
    @Query(
        value = """
            INSERT INTO reserva_ticket (ticket_id, situacao)
            VALUES (:ticketId, 'PENDENTE')
            ON CONFLICT (ticket_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int criarPendenteSeAusente(@Param("ticketId") UUID ticketId);

    /**
     * Trava a reserva do ticket, serializando mensagens diferentes do mesmo ticket.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM ReservaTicket r WHERE r.ticketId = :ticketId")
    Optional<ReservaTicket> buscarParaAlteracao(@Param("ticketId") UUID ticketId);
}
