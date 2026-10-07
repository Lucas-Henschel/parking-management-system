-- Uso: psql -v qtd=75000 -f semear-vagas.sql  (banco vagas)
WITH s AS (
    INSERT INTO setor (id, codigo_setor, status) VALUES (gen_random_uuid(), 'SV', 'ATIVO') RETURNING id
), b AS (
    INSERT INTO bloco (id, setor_id, codigo_bloco, status)
    SELECT gen_random_uuid(), id, 'BV', 'ATIVO' FROM s RETURNING id
), t AS (
    INSERT INTO tipo_vaga (id, nome_tipo) VALUES (gen_random_uuid(), 'Carro') RETURNING id
)
INSERT INTO vaga (id, numero, bloco_id, tipo_id, status)
SELECT gen_random_uuid(), 'V' || g, b.id, t.id, 'LIVRE'
FROM generate_series(1, :qtd) AS g, b, t;
