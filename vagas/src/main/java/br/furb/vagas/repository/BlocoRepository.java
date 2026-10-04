package br.furb.vagas.repository;

import br.furb.vagas.entity.Bloco;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface BlocoRepository extends JpaRepository<Bloco, UUID> {
}
