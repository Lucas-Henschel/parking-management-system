package br.furb.vagas.repository;

import br.furb.vagas.entity.TipoVaga;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TipoVagaRepository extends JpaRepository<TipoVaga, UUID> {
}
