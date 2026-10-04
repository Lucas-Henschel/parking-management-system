package br.furb.vagas.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FluxoReservaRepository {
    private final JdbcTemplate banco;
    public FluxoReservaRepository(JdbcTemplate banco) { this.banco = banco; }
    public boolean registrarMensagem(UUID mensagemId) {
        return banco.update("INSERT INTO mensagem_processada (id) VALUES (?) ON CONFLICT DO NOTHING", mensagemId) == 1;
    }
    public RegistroReserva bloquearTicket(UUID ticketId) {
        banco.update("INSERT INTO reserva_ticket (ticket_id, situacao) VALUES (?, 'PENDENTE') ON CONFLICT DO NOTHING", ticketId);
        return banco.queryForObject("""
                SELECT situacao, vaga_id, numero_vaga FROM reserva_ticket WHERE ticket_id = ? FOR UPDATE
                """, (linha, indice) -> new RegistroReserva(linha.getString("situacao"),
                linha.getObject("vaga_id", UUID.class), linha.getString("numero_vaga")), ticketId);
    }
    public void registrarResultado(UUID ticketId, String situacao, UUID vagaId, String numero) {
        banco.update("UPDATE reserva_ticket SET situacao = ?, vaga_id = ?, numero_vaga = ? WHERE ticket_id = ?",
                situacao, vagaId, numero, ticketId);
    }
    public record RegistroReserva(String situacao, UUID vagaId, String numeroVaga) {}
}
