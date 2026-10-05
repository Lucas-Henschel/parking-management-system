package br.furb.estacionamento.repository;

import br.furb.estacionamento.entity.Ticket;
import br.furb.estacionamento.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    boolean existsByVeiculo_IdAndStatusIn(UUID veiculoId, List<TicketStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select ticket from Ticket ticket join fetch ticket.veiculo where ticket.id = :ticketId")
    Optional<Ticket> findByIdForUpdate(@Param("ticketId") UUID ticketId);

    List<Ticket> findByVeiculo_IdOrderByEntradaDesc(UUID veiculoId);
}