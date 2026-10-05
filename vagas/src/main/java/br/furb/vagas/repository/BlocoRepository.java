package br.furb.vagas.repository;

import br.furb.vagas.entity.Bloco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface BlocoRepository extends JpaRepository<Bloco, UUID> {
}
