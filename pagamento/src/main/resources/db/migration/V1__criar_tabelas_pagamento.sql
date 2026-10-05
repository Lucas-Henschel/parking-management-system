CREATE TABLE metodo_pagamento
(
    id          UUID PRIMARY KEY,
    nome_metodo VARCHAR(50) NOT NULL UNIQUE
);

CREATE TABLE pagamento
(
    id                  UUID PRIMARY KEY,
    ticket_id           UUID           NOT NULL UNIQUE,
    metodo_pagamento_id UUID           NOT NULL,
    valor               NUMERIC(10, 2) NOT NULL,
    data                TIMESTAMP      NOT NULL,
    status              VARCHAR(30)    NOT NULL,
    CONSTRAINT fk_pagamento_metodo
        FOREIGN KEY (metodo_pagamento_id)
            REFERENCES metodo_pagamento (id)
);

INSERT INTO metodo_pagamento (id, nome_metodo)
VALUES ('11111111-1111-1111-1111-111111111111', 'DINHEIRO'),
       ('22222222-2222-2222-2222-222222222222', 'PIX'),
       ('33333333-3333-3333-3333-333333333333', 'CARTAO_CREDITO'),
       ('44444444-4444-4444-4444-444444444444', 'CARTAO_DEBITO');