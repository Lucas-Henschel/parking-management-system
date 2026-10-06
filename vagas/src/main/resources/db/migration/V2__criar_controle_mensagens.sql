CREATE TABLE mensagem_processada (
    id UUID PRIMARY KEY,
    processada_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reserva_ticket (
    ticket_id UUID PRIMARY KEY,
    situacao VARCHAR(20) NOT NULL CHECK (situacao IN ('PENDENTE', 'RESERVADA', 'INDISPONIVEL', 'LIBERADA')),
    vaga_id UUID,
    numero_vaga VARCHAR(30),
    CHECK (situacao <> 'RESERVADA' OR (vaga_id IS NOT NULL AND numero_vaga IS NOT NULL))
);

-- Sem FK para vaga: mantém o histórico do ticket após exclusão de uma vaga liberada.
CREATE TABLE evento_pendente (
    id UUID PRIMARY KEY,
    rota VARCHAR(80) NOT NULL,
    envelope TEXT NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMPTZ
);

CREATE INDEX idx_evento_pendente ON evento_pendente(criado_em, id) WHERE publicado_em IS NULL;
