#!/usr/bin/env bash
# Uso: escala/rodar.sh correcao|vazao [replicas]
set -euo pipefail
cd "$(dirname "$0")/.."

MODO="${1:?uso: escala/rodar.sh correcao|vazao [replicas]}"
REPLICAS="${2:-1}"
case "$MODO" in correcao|vazao) ;; *) echo "Modo inválido: $MODO (use correcao|vazao)" >&2; exit 2 ;; esac
[[ "$REPLICAS" =~ ^[1-9][0-9]*$ ]] || { echo "Réplicas inválidas: $REPLICAS (inteiro positivo)" >&2; exit 2; }

VAGAS="${VAGAS:-100}"
EXTRAS="${EXTRAS:-50}"
PASSOS="${PASSOS:-50,100,200,400}"
DURACAO_S="${DURACAO_S:-20}"

export REPLICAS_ESTACIONAMENTO="$REPLICAS" REPLICAS_VAGAS="$REPLICAS" REPLICAS_PAGAMENTO="$REPLICAS"
PROJETO="parking-escala"
C="docker compose -p $PROJETO -f docker-compose.yml -f docker-compose.escala.yml"
PGUSER_="${POSTGRES_USER:-parking}"
PGPASS_="${POSTGRES_PASSWORD:-parking}"
RMQUSER_="${RABBITMQ_USER:-parking}"
RMQPASS_="${RABBITMQ_PASSWORD:-parking}"
AMOSTRADOR=""
RODADA="$(printf '%04x' $((RANDOM % 65536)))"
RES="escala/resultados/$(date +%Y%m%d-%H%M%S)-$MODO-r$REPLICAS"
mkdir -p "$RES"
RESUMO="$RES/resumo.txt"
FALHAS=0

log() { echo "$*" | tee -a "$RESUMO"; }
psql_db() { local db="$1"; shift; $C exec -T "$db-db" psql -U "$PGUSER_" -d "$db" -tA -F'|' "$@"; }

# Imprime o JSON de /api/queues do RabbitMQ (sem curl).
api_filas() {
  python3 -c '
import base64, sys, urllib.request
cred = (sys.argv[1] + ":" + sys.argv[2]).encode()
req = urllib.request.Request("http://localhost:15672/api/queues")
req.add_header("Authorization", "Basic " + base64.b64encode(cred).decode())
sys.stdout.write(urllib.request.urlopen(req, timeout=5).read().decode())' "$RMQUSER_" "$RMQPASS_"
}

# Soma de mensagens: "ativas" = ready+unacked das filas que não são .dlq; "dlq" = messages das .dlq.
# Campo ausente/nulo é erro (código 1), nunca 0.
somar_filas() {
  api_filas | python3 -c '
import json, sys
modo = sys.argv[1]
total = 0
for q in json.load(sys.stdin):
    dlq = q["name"].endswith(".dlq")
    if modo == "dlq" and dlq:
        campos = ["messages"]
    elif modo == "ativas" and not dlq:
        campos = ["messages_ready", "messages_unacknowledged"]
    else:
        continue
    for c in campos:
        v = q.get(c)
        if v is None:
            sys.exit("campo %s ausente na fila %s" % (c, q["name"]))
        total += v
print(total)' "$1"
}

amostrar() {
  : > "$RES/stats.txt"; echo "instante,fila,mensagens" > "$RES/fila.csv"
  while true; do
    docker stats --no-stream --format '{{.Name}} cpu={{.CPUPerc}} mem={{.MemUsage}}' >> "$RES/stats.txt" 2>/dev/null || true
    api_filas 2>/dev/null | python3 -c '
import json, sys, time
try:
    for q in json.load(sys.stdin):
        print(str(int(time.time())) + "," + q["name"] + "," + str(q.get("messages", 0)))
except Exception:
    pass' >> "$RES/fila.csv" || true
    sleep 5
  done
}

# Retorna 1 se o outbox não esvaziou em ~60 s (ou se a consulta falhou).
aguardar_outbox() {
  local pend db n
  for _ in $(seq 1 30); do
    pend=0
    for db in estacionamento vagas; do
      n="$(psql_db "$db" -c "select count(*) from evento_pendente where publicado_em is null")" || return 1
      pend=$((pend + n))
    done
    [ "$pend" -eq 0 ] && return 0
    sleep 2
  done
  return 1
}

# Retorna 1 se as filas não esvaziaram em ~60 s.
aguardar_filas() {
  local s
  for _ in $(seq 1 30); do
    if s="$(somar_filas ativas 2>/dev/null)" && [ "$s" -eq 0 ]; then return 0; fi
    sleep 2
  done
  return 1
}

checar_invariantes() {
  local fase="$1" db out esperado linhas nome violacoes dlq i
  if aguardar_outbox; then :; else log "  FALHA outbox: eventos pendentes não esvaziaram em 60 s"; FALHAS=$((FALHAS + 1)); fi
  for db in estacionamento vagas pagamento; do
    esperado="$(grep -c "^SELECT '" "escala/sql/invariantes-$db.sql")" || esperado=0
    out="$(psql_db "$db" -v ON_ERROR_STOP=1 -v "fase=$fase" < "escala/sql/invariantes-$db.sql")" \
      || { log "  FALHA $db: psql das invariantes falhou"; FALHAS=$((FALHAS + 1)); continue; }
    linhas=0
    while IFS='|' read -r nome violacoes; do
      if [[ ! "$nome|$violacoes" =~ ^[a-z_]+\|[0-9]+$ ]]; then
        log "  FALHA $db: linha inesperada na saída das invariantes: '$nome|$violacoes'"; FALHAS=$((FALHAS + 1)); continue
      fi
      linhas=$((linhas + 1))
      if [ "$violacoes" -eq 0 ]; then log "  ok    $db: $nome"; else log "  FALHA $db: $nome = $violacoes"; FALHAS=$((FALHAS + 1)); fi
    done <<< "$out"
    if [ "$linhas" -eq 0 ] || [ "$linhas" -ne "$esperado" ]; then
      log "  FALHA $db: $linhas invariantes avaliadas, esperadas $esperado"; FALHAS=$((FALHAS + 1))
    fi
  done

  if aguardar_filas; then
    sleep 6  # a API de gerenciamento atualiza as estatísticas a cada ~5 s
  else
    log "  FALHA rabbitmq: filas não esvaziaram em 60 s"; FALHAS=$((FALHAS + 1))
    sleep 6
  fi
  dlq=""
  for i in 1 2 3; do
    if dlq="$(somar_filas dlq 2>/dev/null)"; then break; fi
    dlq=""; sleep 5
  done
  if [ -z "$dlq" ]; then log "  FALHA rabbitmq: não foi possível ler as DLQs"; FALHAS=$((FALHAS + 1))
  elif [ "$dlq" -eq 0 ]; then log "  ok    rabbitmq: dlq_vazias"
  else log "  FALHA rabbitmq: dlq_vazias = $dlq"; FALHAS=$((FALHAS + 1)); fi
}

k6() { $C run --rm -v "$PWD/$RES":/resultados k6 run -e "RODADA=$RODADA" "$@"; }

log "== $MODO com $REPLICAS réplica(s) por serviço — $(date) =="

# Pré-requisito: o teste usa o projeto compose isolado "$PROJETO", mas o docker-compose.yml
# tem container_name e portas fixos; não pode coexistir com a stack de desenvolvimento.
preflight() {
  local nome proj conflito=""
  for nome in estacionamento-db vagas-db pagamento-db rabbitmq; do
    proj="$(docker inspect -f '{{index .Config.Labels "com.docker.compose.project"}}' "$nome" 2>/dev/null)" || continue
    if [ "$proj" != "$PROJETO" ]; then conflito="$conflito $nome"; fi
  done
  if [ -n "$conflito" ]; then
    echo "ERRO: containers da stack de desenvolvimento em execução:$conflito" >&2
    echo "Pare-a antes com 'docker compose down' (sem -v, para manter os dados) e rode o teste de novo." >&2
    return 1
  fi
}
preflight || exit 1

# Só agora é seguro mexer no projeto $PROJETO (nunca em outro): limpa restos de uma rodada anterior.
trap 'if [ -n "$AMOSTRADOR" ]; then kill "$AMOSTRADOR" 2>/dev/null || true; fi; if [ -z "${MANTER:-}" ]; then $C down -v >/dev/null 2>&1 || true; fi' EXIT
$C down -v >/dev/null 2>&1 || true

PORTAS_OCUPADAS="$(python3 - <<'PY'
import socket
ocupadas = []
for p in (5433, 5434, 5435, 5672, 15672, 8081, 8082, 8083):
    s = socket.socket()
    s.settimeout(0.5)
    if s.connect_ex(("127.0.0.1", p)) == 0:
        ocupadas.append(str(p))
    s.close()
print(" ".join(ocupadas))
PY
)"
if [ -n "$PORTAS_OCUPADAS" ]; then
  echo "ERRO: portas em uso no host: $PORTAS_OCUPADAS (precisam estar livres: 5433-5435, 5672, 15672, 8081-8083)." >&2
  echo "Pare a stack de desenvolvimento ('docker compose down') e os serviços rodando na IDE." >&2
  exit 1
fi

$C up -d --build >/dev/null
amostrar & AMOSTRADOR=$!

case "$MODO" in
  correcao)
    : > "$RES/stats.txt"  # só a janela de carga entra na CPU máxima
    k6 -e FASE=entrada -e "VAGAS=$VAGAS" -e "EXTRAS=$EXTRAS" \
      --summary-export /resultados/summary-entrada.json /scripts/correcao.js || FALHAS=$((FALHAS + 1))

    log "Invariantes após a entrada:"
    checar_invariantes entrada
    ATIVOS="$(psql_db estacionamento -c "select count(*) from ticket where status='ATIVO'")" || ATIVOS=""
    OCUPADAS="$(psql_db vagas -c "select count(*) from vaga where status='OCUPADA'")" || OCUPADAS=""
    if [ -z "$ATIVOS" ] || [ -z "$OCUPADAS" ]; then log "  FALHA cruzado: não foi possível contar tickets/vagas"; FALHAS=$((FALHAS + 1))
    elif [ "$ATIVOS" -eq "$OCUPADAS" ]; then log "  ok    cruzado: tickets ATIVO ($ATIVOS) = vagas OCUPADA ($OCUPADAS)"
    else log "  FALHA cruzado: tickets ATIVO ($ATIVOS) != vagas OCUPADA ($OCUPADAS)"; FALHAS=$((FALHAS + 1)); fi

    psql_db estacionamento -c "select coalesce(json_agg(id), '[]'::json) from ticket where status='ATIVO'" > "$RES/ids.json"
    k6 -e FASE=saida --summary-export /resultados/summary-saida.json /scripts/correcao.js || FALHAS=$((FALHAS + 1))

    log "Invariantes após a saída:"
    checar_invariantes final
    ;;
  vazao)
    # Entradas esperadas: a rampa do k6 parte de PASSOS[0] e sobe linearmente até cada alvo.
    # Semeia 20% a mais; vagas demais encarecem a consulta de reserva no vagas-db.
    TOTAL="$(python3 -c "
import math, sys
p = [int(x) for x in sys.argv[1].split(',')]
d = int(sys.argv[2])
ant, esp = p[0], 0.0
for alvo in p:
    esp += (ant + alvo) / 2 * d
    ant = alvo
print(math.ceil(esp * 1.2))" "$PASSOS" "$DURACAO_S")"
    log "Vagas semeadas: $TOTAL (entradas esperadas x 1,2)"
    # O Flyway do serviço vagas cria as tabelas depois do `up -d`: espera a tabela existir.
    for _ in $(seq 1 60); do
      [ "$(psql_db vagas -c "select to_regclass('public.vaga') is not null and to_regclass('public.setor') is not null")" = "t" ] && break
      sleep 2
    done
    $C exec -T vagas-db psql -U "$PGUSER_" -d vagas -q -v ON_ERROR_STOP=1 -v "qtd=$TOTAL" < escala/sql/semear-vagas.sql
    SEMEADAS="$(psql_db vagas -c "select count(*) from vaga where status='LIVRE'")"
    [ "$SEMEADAS" -ge "$TOTAL" ] || { log "ERRO: semeadas $SEMEADAS de $TOTAL vagas"; exit 1; }
    : > "$RES/stats.txt"  # só a janela de carga entra na CPU máxima
    k6 -e "PASSOS=$PASSOS" -e "DURACAO_S=$DURACAO_S" \
      --summary-export /resultados/summary-vazao.json /scripts/vazao.js || FALHAS=$((FALHAS + 1))

    # Imprime o resumo; o código de saída 3 indica métrica essencial ausente/zerada.
    python3 - "$RES/summary-vazao.json" <<'PY' | tee -a "$RESUMO" || FALHAS=$((FALHAS + 1))
import json, sys
try:
    m = json.load(open(sys.argv[1]))["metrics"]
except Exception as e:
    print(f"FALHA: não foi possível ler o summary-export do k6 ({e})")
    sys.exit(3)
req = m.get("http_reqs{name:entrada}", {})
dur = m.get("http_req_duration{name:entrada}", {})
ativo = m.get("tempo_ate_ativo", {})
desc = int(m.get("dropped_iterations", {}).get("count", 0))
sem_ativo = int(m.get("amostras_sem_ativo", {}).get("count", 0))
checks = m.get("checks", {})
n = int(req.get("count", 0))
if n == 0:
    print("FALHA: http_reqs{name:entrada} ausente ou zero; nenhuma entrada medida")
    sys.exit(3)
print(f"Entradas: {n} (carga oferecida média de {req.get('rate', 0):.1f}/s na rampa)")
print(f"Iterações descartadas: {desc}")
if desc > 0:
    print("AVISO: dropped_iterations > 0 (meta de taxa não atingida)")
print(f"Latência POST /entrada: p95={dur.get('p(95)', 0):.0f} ms  max={dur.get('max', 0):.0f} ms")
if "p(95)" in ativo:
    print(f"Tempo até ATIVO: med={ativo.get('med', 0):.0f} ms  p95={ativo.get('p(95)', 0):.0f} ms")
else:
    print("AVISO: nenhuma amostra de tempo_ate_ativo")
print(f"Amostras que não chegaram a ATIVO: {sem_ativo}")
falhas = int(checks.get("fails", 0))
if falhas > 0:
    print(f"FALHA: {falhas} check(s) falharam (entradas não aceitas com 202)")
    sys.exit(3)
PY
    if aguardar_outbox; then :; else log "AVISO: outbox não esvaziou em 60 s"; fi
    # Espera o backlog de reservas ser decidido (nenhum ticket PENDENTE) antes de contar RECUSADO.
    DRENADO=0
    for _ in $(seq 1 150); do
      PENDENTES="$(psql_db estacionamento -c "select count(*) from ticket where status='PENDENTE'" 2>/dev/null)" || PENDENTES="?"
      if [ "$PENDENTES" = "0" ]; then DRENADO=1; break; fi
      sleep 2
    done
    if [ "$DRENADO" -eq 1 ]; then log "Backlog drenado: nenhum ticket PENDENTE"
    else log "FALHA: ainda há tickets PENDENTE ($PENDENTES) após 300 s; contagens abaixo são parciais"; FALHAS=$((FALHAS + 1)); fi
    # Vazão concluída: decisões de reserva (mensagem_processada no estacionamento) entre a primeira entrada e a última decisão.
    CONCLUIDA="$(psql_db estacionamento -c "select count(*), coalesce(extract(epoch from (max(m.processado_em) - (select min(entrada) from ticket))), 0) from mensagem_processada m")" || CONCLUIDA=""
    python3 - "$CONCLUIDA" <<'PY' | tee -a "$RESUMO"
import sys
try:
    n, seg = sys.argv[1].split("|")
    n, seg = int(n), float(seg)
    if n <= 0 or seg <= 0:
        raise ValueError
    print(f"Vazão concluída: {n / seg:.1f} decisões/s (entre a primeira entrada e a última decisão; {n} decisões em {seg:.0f} s)")
except Exception:
    print("AVISO: vazão concluída indisponível; só a carga oferecida (k6) foi medida")
PY
    RECUSADOS="$(psql_db estacionamento -c "select count(*) from ticket where status='RECUSADO'")" || RECUSADOS="?"
    log "Entradas finalizadas como RECUSADO: $RECUSADOS"
    if [ "$RECUSADOS" != "0" ]; then log "AVISO: há entradas RECUSADO (ou contagem indisponível); as vagas semeadas foram insuficientes."; fi
    if [ "$(wc -l < "$RES/fila.csv")" -le 1 ]; then
      log "AVISO: fila.csv sem amostras; pico de vaga.reservar.queue indisponível"
    else
      FILA_MAX="$(awk -F, 'NR>1 && $2=="vaga.reservar.queue" && $3>m {m=$3} END {print m+0}' "$RES/fila.csv")"
      log "Pico de mensagens em vaga.reservar.queue: $FILA_MAX"
    fi
    log "CPU máxima observada (amostras a cada 5 s):"
    python3 - "$RES/stats.txt" <<'PY' | tee -a "$RESUMO"
import re, sys
mx = {}
for linha in open(sys.argv[1]):
    r = re.match(r"(\S+) cpu=([0-9.]+)%", linha)
    if r:
        mx[r.group(1)] = max(mx.get(r.group(1), 0.0), float(r.group(2)))
if not mx:
    print("AVISO: stats.txt sem amostras de CPU")
for nome, v in sorted(mx.items(), key=lambda kv: -kv[1])[:10]:
    print(f"  {nome}: {v:.1f}%")
PY
    ;;
esac

if [ "$FALHAS" -eq 0 ]; then log "RESULTADO: OK"; else log "RESULTADO: $FALHAS falha(s)"; fi
log "Relatório em $RES"
[ "$FALHAS" -eq 0 ]
