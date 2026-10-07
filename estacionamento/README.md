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
| `POST /teste` | Executa a simulação de entradas, saídas, pagamentos e três tentativas sem vaga |

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

## Simulação do sistema

Com os três serviços e o RabbitMQ rodando, chame `POST http://localhost:8081/teste`, sem corpo:

```bash
curl -X POST http://localhost:8081/teste
```

A rota cria 2 setores ativos, 3 categorias, 3 blocos por setor e 2 vagas por bloco
(12 vagas). Entram 8 carros; 3 saem e pagam com PIX, DINHEIRO e CARTAO_CREDITO.
Depois entram mais carros até ocupar todas as vagas disponíveis, inclusive vagas
anteriores em setores e blocos ativos. A cada pagamento, verifica a finalização do
ticket e a liberação da vaga. A reserva, o cálculo e a liberação usam o RabbitMQ real.

A chamada aguarda a simulação terminar e retorna as etapas, IDs dos tickets e
pagamentos. Em falha, retorna HTTP 502 com o erro e o progresso parcial. Os dados
não são apagados; cada execução usa nomes e placas novos. Execute sem outros
clientes alterando as vagas ao mesmo tempo. Chamadas simultâneas retornam 409.

O campo `evidencias` apresenta uma sequência com horário, etapa, serviço, método,
rota, status HTTP, requisição e resultado. Os mesmos resultados aparecem nos logs
com `[teste <execucao>]`. Cada consulta durante a espera fica registrada; ao
concluir, o registro `ESPERA` informa a quantidade de consultas e a duração.

São consultados os cadastros antes de preparar a estrutura e cada item após criá-lo;
as vagas livres antes e depois de cada entrada; o ticket e sua vaga após a reserva;
o ticket e a vaga antes da saída; o pagamento calculado antes de pagar e o pagamento
pago depois; o ticket finalizado e a vaga liberada; e a disponibilidade ao preencher
o estacionamento. O teste também confere que o valor calculado coincide nos dois serviços.

Após ocupar as vagas, envia um carro excedente e aguarda a recusa. Confere
`tentativasReserva = 3`, saída registrada, ausência de vaga e de valor e ausência de
pagamento (GET retorna 404). Após 2 segundos, consulta novamente para confirmar
que o ticket continua encerrado. As consultas ficam em `evidencias`.

Para comprovar as buscas reais, nos logs do serviço vagas filtre pelo `ticketId`
do carro excedente: devem aparecer 3 registros `Buscando vaga livre` e 3 registros
`Busca sem vagas`, com `messageId` diferentes. Consultas GET podem não capturar os
estados intermediários se o RabbitMQ processar as respostas rapidamente; os logs
das buscas e o contador final complementam essas evidências. Reinicie também o
serviço vagas para carregar a alteração que permite nova busca após indisponibilidade.

As URLs podem ser configuradas com `teste.estacionamento-url`, `teste.vagas-url`
e `teste.pagamento-url` (padrão: localhost nas portas 8081, 8082 e 8083).
`teste.espera-segundos` controla a espera por cada resultado assíncrono (padrão: 30).
As chamadas HTTP dessa rota apenas simulam as ações do cliente nas APIs; a
integração entre os serviços no fluxo de negócio continua via RabbitMQ.
