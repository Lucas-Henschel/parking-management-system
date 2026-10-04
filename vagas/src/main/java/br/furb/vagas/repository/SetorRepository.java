package br.furb.vagas.repository;

import br.furb.vagas.entity.Setor;
import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface SetorRepository extends JpaRepository<Setor, UUID> {
}
