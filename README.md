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

A comunicação entre os serviços é assíncrona, pela exchange `parking.exchange`. As filas, routing keys, o envelope e exemplos de payload de cada mensagem estão em [docs/mensagens-rabbitmq.md](docs/mensagens-rabbitmq.md).

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
- Swagger UI (documentação da API, troque a porta para cada serviço):
  - estacionamento: http://localhost:8081/swagger-ui.html
  - vagas: http://localhost:8082/swagger-ui.html
  - pagamento: http://localhost:8083/swagger-ui.html
- Especificação OpenAPI em JSON: `/v3/api-docs` (ex.: http://localhost:8083/v3/api-docs)
- RabbitMQ: http://localhost:15672 (usuário e senha `parking`)

Para parar a infraestrutura (mantendo os dados) ou apagar tudo:

```bash
docker compose down
docker compose down -v
```

## Teste de escalabilidade

Sobe os três serviços com N réplicas (Docker Compose + Nginx) e roda o k6 contra eles. Precisa só de Docker. Detalhes do Nginx e dos scripts do k6 em [docs/k6-nginx.md](docs/k6-nginx.md).

```bash
escala/rodar.sh correcao 3   # N+M entradas concorrentes, saída e pagamento; confere invariantes no SQL
escala/rodar.sh vazao 2      # rampa de carga crescente de POST /entrada; mede vazão e latência
```

O segundo argumento é o número de réplicas de cada serviço (padrão 1). Os resultados (resumo.txt, fila.csv, stats.txt e os JSON do k6) ficam em escala/resultados/ e não vão para o git. Variáveis opcionais: `VAGAS`, `EXTRAS` (correção) e `PASSOS`, `DURACAO_S` (vazão; padrão `PASSOS=50,100,200,400`, `DURACAO_S=20`).

Atenção:

- O teste usa o projeto compose próprio `parking-escala`: seus volumes são isolados dos da stack de desenvolvimento e são removidos ao final (`down -v` só no projeto `parking-escala`).
- Ele não convive com a stack de desenvolvimento: as portas 5433-5435, 5672, 15672 e 8081-8083 precisam estar livres (inclusive serviços rodando pela IDE). Pare a stack antes com `docker compose down` (sem `-v`); o script aborta com uma mensagem se detectar conflito.
- `MANTER=1 escala/rodar.sh ...` mantém o ambiente de pé no fim, para inspeção. Para derrubá-lo: `docker compose -p parking-escala -f docker-compose.yml -f docker-compose.escala.yml down -v`.
- No modo vazão, o k6 mede a carga oferecida; a "Vazão concluída" é calculada pelas decisões de reserva registradas no banco do estacionamento.

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
