package br.furb.pagamento.repository;

import br.furb.pagamento.entity.MensagemProcessada;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface MensagemProcessadaRepository extends JpaRepository<MensagemProcessada, UUID> {
}
