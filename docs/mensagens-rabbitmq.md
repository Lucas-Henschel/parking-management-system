# Mensagens (RabbitMQ)

Exchange única `parking.exchange` (topic, durável). Cada fila tem uma DLQ `<fila>.dlq`. Quem consome declara a própria fila.

| Mensagem | Publica → consome | Routing key | Fila |
|---|---|---|---|
| `RESERVAR_VAGA` | estacionamento → vagas | `vaga.reservar` | `vaga.reservar.queue` |
| `LIBERAR_VAGA` | estacionamento → vagas | `vaga.liberar` | `vaga.liberar.queue` |
| `VAGA_RESERVADA` | vagas → estacionamento | `vaga.reservada` | `estacionamento.vaga-resultado.queue` |
| `VAGA_INDISPONIVEL` | vagas → estacionamento | `vaga.indisponivel` | `estacionamento.vaga-resultado.queue` |
| `CALCULAR_PAGAMENTO` | estacionamento → pagamento | `pagamento.calcular` | `pagamento.calcular.queue` |
| `PAGAMENTO_CALCULADO` | pagamento → estacionamento | `pagamento.calculado` | `estacionamento.pagamento-calculado.queue` |
| `PAGAMENTO_CONFIRMADO` | pagamento → estacionamento | `pagamento.confirmado` | `estacionamento.pagamento-confirmado.queue` |

## Envelope

Toda mensagem é enviada dentro do mesmo envelope (JSON). `correlationId` é sempre o `ticketId`. Consumidores ignoram `messageId` já processado (idempotência).

| Campo | Tipo | Descrição |
|---|---|---|
| `messageId` | UUID | Identifica a mensagem (idempotência) |
| `correlationId` | UUID | `ticketId`, para rastrear o fluxo entre serviços |
| `tipo` | string | Nome da mensagem (ex.: `RESERVAR_VAGA`) |
| `timestamp` | string | Data/hora em ISO-8601 UTC |
| `payload` | objeto | Conteúdo, conforme a mensagem |

Datas em ISO-8601 UTC (`Instant`), valores em decimal (`20.00`) e IDs em UUID.

## Exemplos de payload

**`RESERVAR_VAGA`** (estacionamento → vagas)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0001",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "RESERVAR_VAGA",
  "timestamp": "2026-10-04T12:00:00Z",
  "payload": {
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c"
  }
}
```

**`VAGA_RESERVADA`** (vagas → estacionamento)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0002",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "VAGA_RESERVADA",
  "timestamp": "2026-10-04T12:00:01Z",
  "payload": {
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
    "vagaId": "5e7d1a90-2c3b-4d6e-8f01-a2b3c4d5e6f7",
    "numeroVaga": "A-12"
  }
}
```

**`VAGA_INDISPONIVEL`** (vagas → estacionamento)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0003",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "VAGA_INDISPONIVEL",
  "timestamp": "2026-10-04T12:00:01Z",
  "payload": {
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
    "motivo": "SEM_VAGAS"
  }
}
```

**`CALCULAR_PAGAMENTO`** (estacionamento → pagamento)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0004",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "CALCULAR_PAGAMENTO",
  "timestamp": "2026-10-04T14:00:00Z",
  "payload": {
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
    "entrada": "2026-10-04T12:00:00Z",
    "saida": "2026-10-04T14:00:00Z"
  }
}
```

**`PAGAMENTO_CALCULADO`** (pagamento → estacionamento)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0005",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "PAGAMENTO_CALCULADO",
  "timestamp": "2026-10-04T14:00:01Z",
  "payload": {
    "pagamentoId": "9d3f4e21-6a7b-4c8d-9e0f-1a2b3c4d5e6f",
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
    "valor": 20.00,
    "data": "2026-10-04T14:00:01Z",
    "status": "CALCULADO"
  }
}
```

**`PAGAMENTO_CONFIRMADO`** (pagamento → estacionamento)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0006",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "PAGAMENTO_CONFIRMADO",
  "timestamp": "2026-10-04T14:05:00Z",
  "payload": {
    "pagamentoId": "9d3f4e21-6a7b-4c8d-9e0f-1a2b3c4d5e6f",
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
    "valor": 20.00,
    "metodo": "PIX",
    "status": "PAGO"
  }
}
```

**`LIBERAR_VAGA`** (estacionamento → vagas)

```json
{
  "messageId": "0b8f6c1e-3d4a-4a57-9a11-6f1c2d7e0007",
  "correlationId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
  "tipo": "LIBERAR_VAGA",
  "timestamp": "2026-10-04T14:05:01Z",
  "payload": {
    "ticketId": "b4a2c9d0-7e55-4f0b-8a3c-1d2e3f4a5b6c",
    "vagaId": "5e7d1a90-2c3b-4d6e-8f01-a2b3c4d5e6f7"
  }
}
```

