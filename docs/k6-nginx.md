# k6 e Nginx no teste de escalabilidade

Este documento explica as duas peças do ambiente de teste de escalabilidade horizontal: o **Nginx**, que distribui as requisições entre as réplicas, e o **k6**, que gera a carga e confere o resultado. Para rodar o teste, veja a seção "Teste de escalabilidade" do [README](../README.md).

## Visão geral

```
k6 ──► balanceador (Nginx) ──► estacionamento (N réplicas) ──┐
                         ├──► vagas          (N réplicas) ───┼──► RabbitMQ + um PostgreSQL por serviço
                         └──► pagamento      (N réplicas) ──┘
```

- Tudo roda na mesma rede do Docker Compose, no projeto `parking-escala` (isolado do ambiente de desenvolvimento).
- Os serviços falam entre si só pelo RabbitMQ. O Nginx e o k6 só tratam o tráfego REST.
- Os consumidores do RabbitMQ não precisam de balanceador: as réplicas de um serviço consomem da mesma fila e competem pelas mensagens.

## Nginx

Arquivo: [`escala/nginx.conf`](../escala/nginx.conf). Serviço `balanceador` em [`docker-compose.escala.yml`](../docker-compose.escala.yml) (imagem `nginx:alpine`).

### O que ele faz

Escuta em três portas, uma por serviço, e repassa tudo para as réplicas daquele serviço:

| Porta | Destino | Uso |
|---|---|---|
| 8081 | `estacionamento` | `POST /entrada`, `POST /tickets/{id}/saida`, `GET /tickets/{id}` |
| 8082 | `vagas` | cadastro de tipos de vaga, setores, blocos e vagas |
| 8083 | `pagamento` | `POST /pagamentos/ticket/{id}/pagar` e consultas |

As três portas também ficam publicadas no host (`localhost:8081` a `8083`). Por isso essas portas precisam estar livres, inclusive de serviços rodando pela IDE.

### Como ele distribui entre as réplicas

O Docker Compose cria um nome DNS por serviço (`estacionamento`, `vagas`, `pagamento`). Quando o serviço tem várias réplicas, esse nome devolve o IP de **todas** elas, e o Nginx alterna entre os IPs (round-robin).

Dois detalhes do arquivo fazem isso funcionar:

- `resolver 127.0.0.11 valid=5s`: o DNS interno do Docker, com resposta válida por 5 segundos.
- `set $destino http://estacionamento:8081; proxy_pass $destino;`: usar uma variável no `proxy_pass` obriga o Nginx a resolver o nome a cada requisição (respeitando o `valid`). Sem a variável, ele resolveria uma vez na subida e ficaria preso nos IPs daquele momento. Com ela, o Nginx também sobe mesmo que nenhuma réplica esteja pronta ainda.

### Ajustes de desempenho

```nginx
worker_processes auto;
events { worker_connections 4096; }
```

Os padrões (1 worker, 512 conexões) poderiam virar o gargalo com centenas de VUs do k6, e distorcer a comparação entre 1, 2 e 3 réplicas.

### Limitações conhecidas

- Com `proxy_pass` por variável não existe `upstream` com keepalive: cada requisição abre uma conexão nova (HTTP/1.0). Em taxas muito altas isso consome portas efêmeras.
- Não há healthcheck das réplicas no Nginx. Uma réplica que acabou de subir pode receber requisição antes de estar pronta e devolver 502. O k6 evita isso esperando `/actuator/health` antes de começar.
- A mudança do número de réplicas é percebida em até ~5 segundos (o `valid` do resolver).

## k6

Scripts em [`escala/k6/`](../escala/k6). O k6 roda em container (`grafana/k6`, versão fixada no compose) dentro da rede do Compose e chama o balanceador (`http://balanceador:8081`, `8082` e `8083`). O serviço `k6` usa o profile `k6`, então só sobe quando é chamado com `docker compose run`. O `escala/rodar.sh` faz essas chamadas; os comandos abaixo servem para rodar um script isolado.

### Arquivos

| Arquivo | Papel |
|---|---|
| `lib.js` | Funções compartilhadas: URLs, `postJson`, `esperarSaude`, `prepararVagas`, `placa`, `aguardarTicket` |
| `correcao.js` | Teste de correção, em duas fases (`entrada` e `saida`) |
| `vazao.js` | Teste de vazão com carga crescente de `POST /entrada` |

### `lib.js`

- `esperarSaude(base)`: consulta `/actuator/health` até responder 200 (até ~2 minutos). Roda no `setup()` dos testes.
- `prepararVagas(qtd)`: cria, via REST em `vagas`, um tipo de vaga, um setor, um bloco e `qtd` vagas.
- `placa(i)`: gera a placa única `<RODADA 4 hex><índice base36 com 5 dígitos>` (9 caracteres, o limite é 10).
- `aguardarTicket(id, estados, timeout)`: consulta `GET /tickets/{id}` a cada 0,5 s até o ticket chegar a um dos estados esperados ou estourar o tempo. Devolve o último estado visto.

### `correcao.js`: o teste de correção

Verifica que, com várias réplicas, o sistema continua certo: nenhuma vaga é reservada duas vezes e todos os tickets chegam a um estado final.

**Fase `entrada`** (`-e FASE=entrada`)
1. Cria `VAGAS` vagas.
2. Dispara `VAGAS + EXTRAS` entradas concorrentes (`POST /entrada`, esperado 202).
3. Espera cada ticket sair de `PENDENTE`.
4. Exige exatamente `VAGAS` tickets `ATIVO`, `EXTRAS` tickets `RECUSADO` e nenhum `PENDENTE`.

**Fase `saida`** (`-e FASE=saida`)
1. Lê `/resultados/ids.json` (os ids dos tickets `ATIVO`, exportados pelo `rodar.sh` a partir do banco). O arquivo vazio ou inválido faz o teste falhar na hora, para não passar sem testar nada.
2. Para cada ticket: `POST /tickets/{id}/saida`, espera `AGUARDANDO_PAGAMENTO`, `POST /pagamentos/ticket/{id}/pagar` com `{"metodo":"PIX"}` e espera `FINALIZADO`.

As duas fases ficam separadas porque, se a saída acontecesse no meio da entrada, vagas liberadas poderiam receber os tickets "extras" e o resultado esperado deixaria de ser exato.

A falha é decidida por **thresholds** do k6 (contadores `tickets_ativo`, `tickets_recusado`, `tickets_pendentes`, `tickets_finalizados` e `checks`). Se algum não bater, o k6 sai com código diferente de 0. Depois, o `rodar.sh` ainda confere os invariantes por SQL (`escala/sql/`) e as DLQs.

Variáveis: `FASE`, `VAGAS` (padrão 100), `EXTRAS` (50), `VUS` (50), `TIMEOUT_S` (60) e `RODADA` (prefixo único de placas e nomes, o `rodar.sh` gera).

### `vazao.js`: o teste de vazão

Aplica uma **rampa** de requisições por segundo em `POST /entrada` (executor `ramping-arrival-rate`). A taxa sobe linearmente passando pelos valores de `PASSOS` (padrão `50,100,200,400`), cada passo durando `DURACAO_S` segundos (padrão 20). Há vagas de sobra, semeadas pelo `rodar.sh`, para que as entradas sigam sempre o caminho completo.

Métricas lidas pelo `rodar.sh` no `--summary-export`:

| Chave | Conteúdo |
|---|---|
| `http_reqs{name:entrada}` | quantidade e média de requisições por segundo na rampa |
| `http_req_duration{name:entrada}` | latência do `POST /entrada` (`p(95)`, `max`) |
| `tempo_ate_ativo` | tempo até o ticket virar `ATIVO`, medido em 1 a cada 20 entradas |
| `amostras_sem_ativo` | amostras que não chegaram a `ATIVO` (recusadas ou com timeout de 120 s) |
| `dropped_iterations` | iterações que o k6 não conseguiu iniciar (ausente quando é 0) |

Os `thresholds` desse arquivo (`max>=0`, `count>=0`) sempre passam: existem só para o k6 incluir essas métricas filtradas por tag no arquivo exportado.

### Rodando um script isolado

Com o ambiente de pé (por exemplo `MANTER=1 escala/rodar.sh ...` ou o compose de escala já iniciado):

```bash
docker compose -p parking-escala -f docker-compose.yml -f docker-compose.escala.yml run --rm k6 run -e RODADA=ab12 -e FASE=entrada -e VAGAS=20 -e EXTRAS=10 /scripts/correcao.js
```

Para a fase `saida`, monte a pasta com o `ids.json` em `/resultados`:

```bash
docker compose -p parking-escala -f docker-compose.yml -f docker-compose.escala.yml run --rm -v "$PWD/escala/resultados/manual":/resultados k6 run -e FASE=saida /scripts/correcao.js
```

## Como interpretar os resultados

- **Correção:** qualquer `FALHA` no `resumo.txt` é um problema real de concorrência no sistema, e não do teste. Guarde a pasta de resultados e investigue pelos logs dos serviços.
- **Vazão:** o k6 mede a **carga oferecida** (a rampa). Para saber quanto o sistema realmente concluiu, o `rodar.sh` calcula a "vazão concluída" pelas decisões de reserva registradas no banco do estacionamento, depois de esperar os tickets saírem de `PENDENTE`.
- A comparação entre 1, 2 e 3 réplicas é de uma execução por configuração, na mesma máquina; trate a diferença como indicativo, não como medida exata.
- O tempo até `ATIVO` só considera as amostras que chegaram a `ATIVO`. Confira também `amostras_sem_ativo` e `dropped_iterations` antes de concluir.
