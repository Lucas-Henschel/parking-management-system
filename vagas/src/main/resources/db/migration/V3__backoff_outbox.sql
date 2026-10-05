ALTER TABLE evento_pendente
    ADD COLUMN tentativas INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN ultimo_erro TEXT;

DROP INDEX idx_evento_pendente;

CREATE INDEX idx_evento_pendente
    ON evento_pendente(proxima_tentativa_em, criado_em, id)
    WHERE publicado_em IS NULL;
