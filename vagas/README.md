# Serviço de Vagas

Implementação do serviço `br.furb.vagas` definido no [quadro do projeto](https://miro.com/app/board/uXjVHlaS4qE=/) e no README da raiz.
Java 21, Spring Boot 4.1, JPA, PostgreSQL, Flyway e RabbitMQ. Porta padrão: **8082**.

## Execução

Na raiz do repositório, execute `docker compose up -d vagas-db rabbitmq`.
Neste diretório, execute `.\mvnw.cmd spring-boot:run` no Windows ou `./mvnw spring-boot:run` no Linux.
As migrations são aplicadas automaticamente. O banco inicia vazio; cadastre setor, bloco, tipo e vaga, nessa ordem.

- Swagger: http://localhost:8082/swagger-ui.html
- OpenAPI: http://localhost:8082/v3/api-docs
- Health: http://localhost:8082/actuator/health

As variáveis DB_URL, DB_USER, DB_PASSWORD, RABBITMQ_HOST, RABBITMQ_PORT,
RABBITMQ_USER, RABBITMQ_PASSWORD e SERVER_PORT seguem o README da raiz.

## API

| Recurso | Endpoints |
|---|---|
| Setores | POST/GET /setores; GET/PUT/DELETE /setores/{id} |
| Blocos | POST/GET /blocos; GET/PUT/DELETE /blocos/{id} |
| Tipos | POST/GET /tipos-vaga; GET/PUT/DELETE /tipos-vaga/{id} |
| Vagas | POST/GET /vagas; GET/PUT/DELETE /vagas/{id} |
| Bloqueio | PATCH /vagas/{id}/bloqueio, corpo: {"bloqueada":true} |
| Ocupação | GET /vagas/ocupacao |

Listagens são paginadas: `?page=0&size=20&sort=id,asc`, limite de 100 itens.
POST retorna 201 com Location, DELETE retorna 204. Erros retornam ProblemDetail:
400 para dados inválidos, 404 para recurso inexistente e 409 para conflitos de negócio/integridade.

Exemplos de corpos, substituindo os IDs retornados:

```json
{"codigo":"A","status":"ATIVO"}
```

POST /setores; em seguida POST /blocos:

```json
{"setorId":"UUID_DO_SETOR","codigo":"1","status":"ATIVO"}
```

POST /tipos-vaga:

```json
{"nome":"COMUM"}
```

POST /vagas:

```json
{"numero":"A-12","blocoId":"UUID_DO_BLOCO","tipoId":"UUID_DO_TIPO"}
```

Setores e blocos aceitam ATIVO/INATIVO. Uma vaga nasce LIVRE e pode ser BLOQUEADA.
OCUPADA é controlado exclusivamente pelas mensagens. Não há chamadas HTTP entre serviços.
Vagas ocupadas não podem ser editadas, bloqueadas ou excluídas. A exclusão de cadastros
referenciados é impedida pelo banco. Números de vaga são únicos no estacionamento;
códigos de bloco são únicos por setor; nomes de tipo e códigos de setor são únicos.

O resumo informa total, disponíveis (LIVRE em bloco e setor ativos), ocupadas e bloqueadas.
Vagas livres em cadastros inativos entram no total, mas não nas disponíveis.
Inativar setor/bloco impede novas reservas e permite liberar as ocupações existentes.
Tipos são classificatórios; RESERVAR_VAGA não traz filtro por tipo no contrato atual.

## Mensageria e consistência

Exchange topic durável parking.exchange. O serviço declara apenas suas filas consumidoras:

| Consome | Fila | Routing key |
|---|---|---|
| RESERVAR_VAGA | vaga.reservar.queue | vaga.reservar |
| LIBERAR_VAGA | vaga.liberar.queue | vaga.liberar |

Cada fila tem uma DLQ durável de mesmo nome acrescido de .dlq, roteada pela exchange padrão.
Erros de processamento têm tentativas limitadas antes de rejeição para DLQ.
Mensagens inválidas, incluindo correlationId diferente de ticketId, são rejeitadas.

VAGA_RESERVADA publica em vaga.reservada; VAGA_INDISPONIVEL em vaga.indisponivel.
Os campos messageId, correlationId, tipo, timestamp e payload preservam o contrato da raiz;
os identificadores do código próprio e os métodos de negócio estão em português.

A seleção usa FOR UPDATE OF v SKIP LOCKED e ignora blocos/setores inativos.
O bloqueio por ticket serializa comandos concorrentes para o mesmo ticket.
Ocupação, inbox (mensagem_processada), histórico (reserva_ticket) e outbox (evento_pendente)
participam da mesma transação PostgreSQL. O ACK do consumidor ocorre após o commit.

O publicador envia mensagens persistentes com mandatory, aguarda confirmação do broker e
verifica devoluções por falta de rota. Falhas mantêm o evento pendente. A fila de resultado
deve ser declarada pelo consumidor Estacionamento; até existir, as respostas permanecem na outbox.
OUTBOX_INTERVALO_MS controla o intervalo (padrão 1000); OUTBOX_HABILITADA permite desativar o publicador.

A entrega é pelo menos uma vez: uma queda após o publish e antes do commit pode reenviar
a mesma resposta com o mesmo messageId. O consumidor deve aplicar a idempotência prevista no contrato.
Mensagens repetidas com o mesmo messageId são ignoradas. Novas mensagens para ticket já
reservado repetem seu resultado, sem ocupar outra vaga. SEM_VAGAS é resultado terminal para
aquele ticket; uma nova tentativa de entrada precisa de novo ticket. Ticket liberado não
volta a reservar e responde TICKET_FINALIZADO. Liberação anterior à reserva encerra o ticket.
Liberação de ticket reservado com vaga divergente é rejeitada e não altera a ocupação.

Organização de pacotes alinhada ao serviço de Pagamento: config, controller, dto,
entity, exception, messaging, repository e service. As classes seguem os sufixos
Controller, Service, Repository, Listener, Publisher, Request, Response, Event, Status e Exception.
As regras de negócio e a
integração RabbitMQ continuam separadas por responsabilidade.
JPA e JdbcTemplate compartilham a conexão e a transação do JpaTransactionManager.
Não há exclusão automática do histórico, inbox ou outbox; definir retenção conforme
o período de reenvio das mensagens e a necessidade de auditoria.

## Testes

```powershell
.\mvnw.cmd test
```

Os testes de domínio, envelopes, API e publicador não dependem de serviços externos.
Os testes PostgreSQL são habilitados quando VAGAS_TEST_DB_URL está definida:

```powershell
$env:VAGAS_TEST_DB_URL = 'jdbc:postgresql://localhost:5434/vagas'
$env:VAGAS_TEST_DB_USER = 'parking'
$env:VAGAS_TEST_DB_PASSWORD = 'parking'
.\mvnw.cmd test
```

A suíte de integração cria um schema exclusivo com UUID, aplica a migration e remove apenas
esse schema ao terminar; precisa de permissão CREATE no banco. Testa atomicidade, rollback,
idempotência, seleção por cadastro ativo e concorrência entre tickets e no mesmo ticket.
Sem a variável, esses testes aparecem como ignorados. RabbitMQ real e o fluxo completo com
Estacionamento precisam de infraestrutura disponível para validação ponta a ponta.
