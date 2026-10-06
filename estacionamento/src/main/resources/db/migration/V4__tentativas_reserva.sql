ALTER TABLE ticket ADD COLUMN tentativas_reserva INTEGER NOT NULL DEFAULT 1;
ALTER TABLE ticket ADD CONSTRAINT ck_ticket_tentativas_reserva CHECK (tentativas_reserva BETWEEN 1 AND 3);
