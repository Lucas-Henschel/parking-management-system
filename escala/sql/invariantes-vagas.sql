SELECT 'vaga_ocupada_sem_reserva', count(*)
FROM vaga v
WHERE v.status = 'OCUPADA'
  AND NOT EXISTS (
      SELECT 1 FROM reserva_ticket r
      WHERE r.ticket_id = v.ticket_id AND r.vaga_id = v.id AND r.situacao = 'RESERVADA'
  )
UNION ALL
SELECT 'eventos_sem_publicar', count(*) FROM evento_pendente WHERE publicado_em IS NULL
UNION ALL
SELECT 'vagas_nao_livres_apos_saida', count(*)
FROM vaga
WHERE :'fase' = 'final' AND status <> 'LIVRE';
