# Serviço de Pagamento

Java 21, Spring Boot, PostgreSQL e RabbitMQ. Porta padrão: **8083**.

Calcula o valor do ticket (R$ 10,00 por hora, arredondando para cima, mínimo de 1 hora; configurável em `PAGAMENTO_VALOR_HORA`) e registra o pagamento em duas etapas: cálculo (`CALCULADO`) e pagamento (`PAGO`).

Na raiz, execute `docker compose up -d pagamento-db rabbitmq`. Neste diretório,
execute `.\mvnw.cmd spring-boot:run` (Windows) ou `./mvnw spring-boot:run` (Linux).
O Flyway aplica as migrations automaticamente.

Swagger: http://localhost:8083/swagger-ui.html. Health: http://localhost:8083/actuator/health.

## API

| Rota | Descrição |
|---|---|
| `POST /pagamentos/ticket/{ticketId}/pagar` | Paga o ticket calculado. Corpo: `{ "metodo": "PIX" }` |
| `GET /pagamentos/{id}` | Consulta o pagamento pelo id |
| `GET /pagamentos/ticket/{ticketId}` | Consulta o pagamento pelo ticket |

Métodos aceitos: `DINHEIRO`, `PIX`, `CARTAO_CREDITO` e `CARTAO_DEBITO`.

## Mensagens

| Mensagem | Direção | Routing key | Fila |
|---|---|---|---|
| `CALCULAR_PAGAMENTO` | consome | `pagamento.calcular` | `pagamento.calcular.queue` (DLQ `pagamento.calcular.queue.dlq`) |
| `PAGAMENTO_CALCULADO` | publica | `pagamento.calculado` | consumida pelo estacionamento |
| `PAGAMENTO_CONFIRMADO` | publica | `pagamento.confirmado` | consumida pelo estacionamento |

Os eventos publicados passam por uma outbox (tabela `evento_pendente`), gravada na mesma transação do pagamento e enviada ao RabbitMQ em segundo plano, com confirmação do broker e nova tentativa em caso de falha.

Formato do envelope e exemplos de payload em [docs/mensagens-rabbitmq.md](../docs/mensagens-rabbitmq.md).
