# Serviço de Vagas

Java 21, Spring Boot, PostgreSQL e RabbitMQ. Porta padrão: **8082**.

Mantém tipos de vaga, setores, blocos e vagas, e controla a ocupação. Ao receber `RESERVAR_VAGA`, escolhe automaticamente a primeira vaga `LIVRE` de um setor e bloco ativos (com `FOR UPDATE SKIP LOCKED`, para que reservas concorrentes nunca peguem a mesma vaga) e a marca como `OCUPADA` para o ticket. Sem vaga livre, responde `VAGA_INDISPONIVEL`.

Na raiz, execute `docker compose up -d vagas-db rabbitmq`. Neste diretório,
execute `.\mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run` (Linux).
O Flyway aplica as migrations automaticamente. O serviço não traz vagas cadastradas: crie tipo de vaga, setor, bloco e vagas pela API.

Swagger: http://localhost:8082/swagger-ui.html. Health: http://localhost:8082/actuator/health.

## API

| Rota | Descrição |
|---|---|
| `POST /tipos-vaga`, `GET /tipos-vaga`, `GET /tipos-vaga/{id}` | Tipos de vaga. Corpo: `{ "nome": "Carro" }` |
| `POST /setores`, `GET /setores`, `GET /setores/{id}` | Setores. Corpo: `{ "codigo": "A", "status": "ATIVO" }` |
| `POST /blocos`, `GET /blocos`, `GET /blocos/{id}` | Blocos. Corpo: `{ "setorId": "<uuid>", "codigo": "1", "status": "ATIVO" }` |
| `POST /vagas`, `GET /vagas`, `GET /vagas/{id}` | Vagas. Corpo: `{ "numero": "A-01", "blocoId": "<uuid>", "tipoId": "<uuid>" }` |

Setores e blocos aceitam `ATIVO` ou `INATIVO`. Uma vaga nasce `LIVRE` e pode ficar `OCUPADA` ou `BLOQUEADA`. Erros: 400 para dados inválidos, 404 para recurso inexistente e 409 para conflitos.

## Mensagens

| Mensagem | Direção | Routing key | Fila |
|---|---|---|---|
| `RESERVAR_VAGA` | consome | `vaga.reservar` | `vaga.reservar.queue` (DLQ `vaga.reservar.queue.dlq`) |
| `LIBERAR_VAGA` | consome | `vaga.liberar` | `vaga.liberar.queue` (DLQ `vaga.liberar.queue.dlq`) |
| `VAGA_RESERVADA` | publica | `vaga.reservada` | consumida por estacionamento |
| `VAGA_INDISPONIVEL` | publica | `vaga.indisponivel` | consumida por estacionamento |

Os eventos publicados passam por uma outbox (tabela `evento_pendente`), enviada ao RabbitMQ em segundo plano.

Formato do envelope e exemplos de payload em [docs/mensagens-rabbitmq.md](../docs/mensagens-rabbitmq.md).
