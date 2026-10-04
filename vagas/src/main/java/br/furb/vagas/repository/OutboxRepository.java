package br.furb.vagas.repository;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {
    private final JdbcTemplate banco;
    public OutboxRepository(JdbcTemplate banco) { this.banco = banco; }
    public void registrar(UUID id, String rota, String envelope) {
        banco.update("INSERT INTO evento_pendente (id, rota, envelope) VALUES (?, ?, ?)", id, rota, envelope);
    }
    public Optional<EventoPendente> buscarParaPublicacao() {
        return banco.query("""
                SELECT id, rota, envelope FROM evento_pendente WHERE publicado_em IS NULL
                ORDER BY criado_em, id LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (linha, indice) -> new EventoPendente(linha.getObject("id", UUID.class),
                linha.getString("rota"), linha.getString("envelope"))).stream().findFirst();
    }
    public void confirmarPublicacao(UUID id) {
        banco.update("UPDATE evento_pendente SET publicado_em = CURRENT_TIMESTAMP WHERE id = ?", id);
    }
    public record EventoPendente(UUID id, String rota, String envelope) {}
}
