# Parking Management System

Sistema de gerenciamento de estacionamento em microsserviços, desenvolvido para a disciplina de Sistemas Distribuídos (FURB).

## Arquitetura

Três serviços independentes, cada um com seu próprio banco PostgreSQL. Não há acesso direto entre bancos nem chamadas diretas entre serviços: toda a integração acontece de forma assíncrona via RabbitMQ.

| Serviço | Responsabilidade | Porta | Banco (porta host) |
|---|---|---|---|
| `estacionamento` | Veículos, tickets, entrada e saída | 8081 | `estacionamento` (5433) |
| `vagas` | Setores, blocos, vagas e ocupação | 8082 | `vagas` (5434) |
| `pagamento` | Cálculo, registro e histórico de pagamentos | 8083 | `pagamento` (5435) |

RabbitMQ: AMQP em `5672`, painel de administração em http://localhost:15672.

## Mensagens (RabbitMQ)

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

### Envelope

Toda mensagem é enviada dentro do mesmo envelope (JSON). `correlationId` é sempre o `ticketId`. Consumidores ignoram `messageId` já processado (idempotência).

| Campo | Tipo | Descrição |
|---|---|---|
| `messageId` | UUID | Identifica a mensagem (idempotência) |
| `correlationId` | UUID | `ticketId`, para rastrear o fluxo entre serviços |
| `tipo` | string | Nome da mensagem (ex.: `RESERVAR_VAGA`) |
| `timestamp` | string | Data/hora em ISO-8601 UTC |
| `payload` | objeto | Conteúdo, conforme a mensagem |

Datas em ISO-8601 UTC (`Instant`), valores em decimal (`20.00`) e IDs em UUID.

### Exemplos de payload

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

## Stack

- Java 21, Spring Boot 4.1, Spring Web, Spring Data JPA, Spring AMQP
- PostgreSQL 17, RabbitMQ 4
- Flyway (migrations), Actuator (health check), springdoc-openapi (Swagger UI)
- Docker Compose para a infraestrutura

## Pré-requisitos

- JDK 21
- Docker com Docker Compose

## Como executar

Suba a infraestrutura (3 bancos + RabbitMQ):

```bash
docker compose up -d
```

Em outro terminal, suba cada serviço (os wrappers Maven já estão no repositório):

```bash
cd estacionamento && ./mvnw spring-boot:run
cd vagas && ./mvnw spring-boot:run
cd pagamento && ./mvnw spring-boot:run
```

Verificações:

- Health: http://localhost:8081/actuator/health (8082 e 8083 para os demais)
- Swagger UI: http://localhost:8081/swagger-ui.html
- RabbitMQ: http://localhost:15672 (usuário e senha `parking`)

Para parar a infraestrutura (mantendo os dados) ou apagar tudo:

```bash
docker compose down
docker compose down -v
```

## Configuração

Não é necessário criar nenhum arquivo: os padrões do `docker-compose.yml` e dos `application.properties` já funcionam juntos (usuário e senha `parking`).

Opcional: para trocar as credenciais do Docker, copie `.env.example` para `.env` e edite. Os serviços aceitam sobrescrita por variáveis de ambiente (`DB_URL`, `DB_USER`, `DB_PASSWORD`, `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `SERVER_PORT`). Se mudar as credenciais, use os mesmos valores nos dois lados.

## Estrutura do repositório

```
.
├── docker-compose.yml
├── estacionamento/   # Spring Boot (br.furb.estacionamento)
├── vagas/            # Spring Boot (br.furb.vagas)
└── pagamento/        # Spring Boot (br.furb.pagamento)
```

Cada serviço mantém suas migrations Flyway em `src/main/resources/db/migration`.
