package br.furb.pagamento.repository;

import br.furb.pagamento.entity.MetodoPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MetodoPagamentoRepository extends JpaRepository<MetodoPagamento, UUID> {
    
    @Query("SELECT m FROM MetodoPagamento m WHERE UPPER(m.nomeMetodo) = UPPER(:nomeMetodo)")
    Optional<MetodoPagamento> findByNomeMetodoIgnoreCase(@Param("nomeMetodo") String nomeMetodo);
}
