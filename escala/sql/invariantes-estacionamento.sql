SELECT 'tickets_pendentes', count(*) FROM ticket WHERE status = 'PENDENTE'
UNION ALL
SELECT 'eventos_sem_publicar', count(*) FROM evento_pendente WHERE publicado_em IS NULL
UNION ALL
SELECT 'ticket_ativo_sem_vaga', count(*) FROM ticket WHERE status = 'ATIVO' AND vaga_id IS NULL
UNION ALL
SELECT 'vaga_em_dois_tickets_abertos', count(*) FROM (
    SELECT vaga_id FROM ticket
    WHERE vaga_id IS NOT NULL AND status IN ('ATIVO', 'AGUARDANDO_PAGAMENTO')
    GROUP BY vaga_id HAVING count(*) > 1
) d
UNION ALL
SELECT 'tickets_nao_finalizados_apos_saida', count(*)
FROM ticket
WHERE :'fase' = 'final' AND status NOT IN ('FINALIZADO', 'RECUSADO');
