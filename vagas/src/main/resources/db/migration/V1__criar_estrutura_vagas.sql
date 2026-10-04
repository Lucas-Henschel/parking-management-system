CREATE TABLE setor (
    id UUID PRIMARY KEY,
    codigo_setor VARCHAR(30) NOT NULL UNIQUE CHECK (length(trim(codigo_setor)) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'INATIVO'))
);
CREATE TABLE bloco (
    id UUID PRIMARY KEY,
    setor_id UUID NOT NULL REFERENCES setor(id),
    codigo_bloco VARCHAR(30) NOT NULL CHECK (length(trim(codigo_bloco)) > 0),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ATIVO', 'INATIVO')),
    UNIQUE (setor_id, codigo_bloco)
);
CREATE TABLE tipo_vaga (
    id UUID PRIMARY KEY,
    nome_tipo VARCHAR(80) NOT NULL UNIQUE CHECK (length(trim(nome_tipo)) > 0)
);
CREATE TABLE vaga (
    id UUID PRIMARY KEY,
    numero VARCHAR(30) NOT NULL UNIQUE CHECK (length(trim(numero)) > 0),
    bloco_id UUID NOT NULL REFERENCES bloco(id),
    tipo_id UUID NOT NULL REFERENCES tipo_vaga(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('LIVRE', 'OCUPADA', 'BLOQUEADA')),
    ticket_id UUID UNIQUE,
    CHECK ((status = 'OCUPADA' AND ticket_id IS NOT NULL) OR (status <> 'OCUPADA' AND ticket_id IS NULL))
);
CREATE INDEX idx_bloco_setor ON bloco(setor_id);
CREATE INDEX idx_vaga_bloco ON vaga(bloco_id);
CREATE INDEX idx_vaga_tipo ON vaga(tipo_id);
CREATE INDEX idx_vaga_livre ON vaga(bloco_id, numero) WHERE status = 'LIVRE';
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
