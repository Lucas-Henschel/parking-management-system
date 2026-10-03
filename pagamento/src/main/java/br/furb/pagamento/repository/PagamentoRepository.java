package br.furb.pagamento.repository;

import br.furb.pagamento.entity.Pagamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PagamentoRepository extends JpaRepository<Pagamento, UUID> {

    Optional<Pagamento> findByTicketId(UUID ticketId);

    List<Pagamento> findAllByTicketIdOrderByDataDesc(UUID ticketId);
}
