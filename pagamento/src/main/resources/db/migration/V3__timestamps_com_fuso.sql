-- Datas passam a ser instantes (UTC), alinhado ao uso de java.time.Instant.
ALTER TABLE pagamento
    ALTER COLUMN data TYPE TIMESTAMP WITH TIME ZONE USING data AT TIME ZONE 'UTC';

ALTER TABLE mensagem_processada
    ALTER COLUMN processado_em TYPE TIMESTAMP WITH TIME ZONE USING processado_em AT TIME ZONE 'UTC';
