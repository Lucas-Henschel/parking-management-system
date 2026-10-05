
ALTER TABLE pagamento ALTER COLUMN metodo_pagamento_id DROP NOT NULL;

CREATE TABLE mensagem_processada (
    message_id UUID PRIMARY KEY,
    processado_em TIMESTAMP NOT NULL
);
