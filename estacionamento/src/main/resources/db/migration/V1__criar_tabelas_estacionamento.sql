CREATE TABLE veiculo (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    placa VARCHAR(10) NOT NULL UNIQUE
);

CREATE TABLE ticket (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    veiculo_id UUID NOT NULL,
    vaga_id UUID,
    entrada TIMESTAMPTZ NOT NULL,
    saida TIMESTAMPTZ,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    valor NUMERIC(10, 2),
    CONSTRAINT fk_ticket_veiculo FOREIGN KEY (veiculo_id) REFERENCES veiculo (id),
    CONSTRAINT ck_ticket_saida CHECK (saida IS NULL OR saida >= entrada),
    CONSTRAINT ck_ticket_valor CHECK (valor IS NULL OR valor >= 0)
);

CREATE INDEX idx_ticket_veiculo_entrada ON ticket (veiculo_id, entrada DESC);

CREATE UNIQUE INDEX uk_ticket_veiculo_em_aberto ON ticket (veiculo_id) WHERE status IN ('PENDENTE', 'ATIVO', 'AGUARDANDO_PAGAMENTO');