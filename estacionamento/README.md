# Serviço de Estacionamento

Java 21, Spring Boot, PostgreSQL e RabbitMQ. Porta padrão: **8081**.

Controla veículos e tickets. A entrada é assíncrona: o ticket nasce `PENDENTE` e passa a `ATIVO` (vaga reservada) ou `RECUSADO` (sem vaga). Na saída, o serviço pede o cálculo do pagamento (`AGUARDANDO_PAGAMENTO`) e, com o pagamento confirmado, finaliza o ticket (`FINALIZADO`) e libera a vaga.

Na raiz, execute `docker compose up -d estacionamento-db rabbitmq`. Neste diretório,
execute `.\mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run` (Linux).
O Flyway aplica as migrations automaticamente.

Swagger: http://localhost:8081/swagger-ui.html. Health: http://localhost:8081/actuator/health.

## API

| Rota | Descrição |
|---|---|
| `POST /entrada` | Registra a entrada. Corpo: `{ "placa": "ABC1D23" }`. Responde 202 com o ticket `PENDENTE` |
| `POST /tickets/{ticketId}/saida` | Registra a saída e solicita o cálculo do pagamento. Responde 202 |
| `GET /tickets/{ticketId}` | Consulta o ticket (estado, vaga, valor) |

O pagamento é feito no serviço de Pagamento (`POST /pagamentos/ticket/{ticketId}/pagar`).

## Mensagens

| Mensagem | Direção | Routing key | Fila |
|---|---|---|---|
| `RESERVAR_VAGA` | publica | `vaga.reservar` | consumida por vagas |
| `LIBERAR_VAGA` | publica | `vaga.liberar` | consumida por vagas |
| `CALCULAR_PAGAMENTO` | publica | `pagamento.calcular` | consumida por pagamento |
| `VAGA_RESERVADA` e `VAGA_INDISPONIVEL` | consome | `vaga.reservada`, `vaga.indisponivel` | `estacionamento.vaga-resultado.queue` |
| `PAGAMENTO_CALCULADO` | consome | `pagamento.calculado` | `estacionamento.pagamento-calculado.queue` |
| `PAGAMENTO_CONFIRMADO` | consome | `pagamento.confirmado` | `estacionamento.pagamento-confirmado.queue` |

Cada fila tem uma DLQ `<fila>.dlq`. Os eventos publicados passam por uma outbox (tabela `evento_pendente`), enviada ao RabbitMQ em segundo plano.

Formato do envelope e exemplos de payload em [docs/mensagens-rabbitmq.md](../docs/mensagens-rabbitmq.md).
