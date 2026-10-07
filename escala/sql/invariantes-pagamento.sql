SELECT 'pagamentos_duplicados_por_ticket', count(*) FROM (
    SELECT ticket_id FROM pagamento GROUP BY ticket_id HAVING count(*) > 1
) d
UNION ALL
SELECT 'pagamentos_nao_pagos_apos_saida', count(*)
FROM pagamento
WHERE :'fase' = 'final' AND status <> 'PAGO';
