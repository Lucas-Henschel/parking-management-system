CREATE TABLE evento_pendente (
    id UUID PRIMARY KEY,
    rota VARCHAR(100) NOT NULL,
    envelope TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL,
    publicado_em TIMESTAMPTZ,
    tentativas INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa TIMESTAMPTZ NOT NULL,
    ultimo_erro VARCHAR(1000)
);

CREATE INDEX idx_evento_pendente_publicacao ON evento_pendente (proxima_tentativa, criado_em)
    WHERE publicado_em IS NULL;

ALTER TABLE ticket ADD COLUMN pagamento_id UUID;
ALTER TABLE ticket ADD COLUMN valor_confirmado NUMERIC(10, 2);
ALTER TABLE ticket ADD CONSTRAINT ck_ticket_valor_confirmado CHECK (valor_confirmado IS NULL OR valor_confirmado >= 0);
