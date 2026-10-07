-- Eventos publicados pelo Pagamento: gravados na mesma transação do estado e enviados ao
-- RabbitMQ em segundo plano, para que uma falha do broker não perca PAGAMENTO_CALCULADO/CONFIRMADO.
CREATE TABLE evento_pendente (
    id UUID PRIMARY KEY,
    rota VARCHAR(80) NOT NULL,
    envelope TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMPTZ,
    tentativas INTEGER NOT NULL DEFAULT 0,
    proxima_tentativa_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_erro TEXT
);

CREATE INDEX idx_evento_pendente
    ON evento_pendente(proxima_tentativa_em, criado_em, id)
    WHERE publicado_em IS NULL;
