package br.furb.estacionamento.repository;

import br.furb.estacionamento.entity.Ticket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    List<Ticket> findByVeiculo_IdOrderByEntradaDesc(UUID veiculoId);
}