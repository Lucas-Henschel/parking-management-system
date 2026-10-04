package br.furb.pagamento.repository;

import br.furb.pagamento.entity.MetodoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface MetodoPagamentoRepository extends JpaRepository<MetodoPagamento, UUID> {
    Optional<MetodoPagamento> findByNomeMetodo(String nomeMetodo);
}
