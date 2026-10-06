CREATE TABLE mensagem_processada (
    message_id UUID PRIMARY KEY,
    processado_em TIMESTAMPTZ NOT NULL
);