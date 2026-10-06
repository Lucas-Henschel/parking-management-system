package br.furb.vagas.repository;

import br.furb.vagas.entity.Setor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SetorRepository extends JpaRepository<Setor, UUID> {
}
