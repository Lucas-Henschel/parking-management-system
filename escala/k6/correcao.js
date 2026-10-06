import http from 'k6/http';
import exec from 'k6/execution';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import {
  EST, PAG, esperarSaude, prepararVagas, postJson, aguardarTicket, placa, VAGAS_URL,
} from './lib.js';

const FASE = __ENV.FASE || 'entrada';
const N = parseInt(__ENV.VAGAS || '100');
const M = parseInt(__ENV.EXTRAS || '50');
const VUS = parseInt(__ENV.VUS || '50');

function exigirInteiro(nome, valor, minimo) {
  if (!Number.isFinite(valor) || !Number.isInteger(valor) || valor < minimo) {
    throw new Error(`${nome} deve ser um inteiro >= ${minimo} (recebido: ${valor})`);
  }
}
exigirInteiro('VAGAS', N, 1);
exigirInteiro('EXTRAS', M, 0);
exigirInteiro('VUS', VUS, 1);

if (FASE !== 'entrada' && FASE !== 'saida') {
  throw new Error(`FASE deve ser 'entrada' ou 'saida' (recebido: ${FASE})`);
}

let IDS = [];
if (FASE === 'saida') {
  IDS = JSON.parse(open('/resultados/ids.json'));
  if (!Array.isArray(IDS) || IDS.length === 0) {
    throw new Error('/resultados/ids.json deve ser um array JSON não vazio de ids de tickets ATIVO');
  }
}

const ativos = new Counter('tickets_ativo');
const recusados = new Counter('tickets_recusado');
const pendentes = new Counter('tickets_pendentes');
const finalizados = new Counter('tickets_finalizados');

const iteracoes = FASE === 'entrada' ? N + M : IDS.length;

export const options = {
  scenarios: {
    correcao: {
      executor: 'shared-iterations',
      vus: Math.max(1, Math.min(VUS, iteracoes)),
      iterations: iteracoes,
      maxDuration: '15m',
    },
  },
  thresholds: FASE === 'entrada'
    ? {
        tickets_ativo: [`count==${N}`],
        tickets_recusado: [`count==${M}`],
        tickets_pendentes: ['count==0'],
        checks: ['rate==1'],
      }
    : {
        tickets_finalizados: [`count==${IDS.length}`],
        checks: ['rate==1'],
      },
};

export function setup() {
  esperarSaude(EST);
  esperarSaude(VAGAS_URL);
  esperarSaude(PAG);
  if (FASE === 'entrada') prepararVagas(N);
}

function entrar() {
  const i = exec.scenario.iterationInTest;
  const r = postJson(`${EST}/entrada`, { placa: placa(i) }, { name: 'entrada' });
  const aceita = check(r, { 'entrada aceita (202)': (x) => x.status === 202 });
  if (!aceita) {
    ativos.add(0); recusados.add(0); pendentes.add(1);
    return;
  }
  const status = aguardarTicket(r.json('id'), ['ATIVO', 'RECUSADO']);
  ativos.add(status === 'ATIVO' ? 1 : 0);
  recusados.add(status === 'RECUSADO' ? 1 : 0);
  pendentes.add(status === 'ATIVO' || status === 'RECUSADO' ? 0 : 1);
}

function sair() {
  const id = IDS[exec.scenario.iterationInTest];

  const saida = postJson(`${EST}/tickets/${id}/saida`, {}, { name: 'saida' });
  const saiu = check(saida, { 'saída aceita (202)': (x) => x.status === 202 });
  const calculado = saiu && aguardarTicket(id, ['AGUARDANDO_PAGAMENTO']) === 'AGUARDANDO_PAGAMENTO';
  check(calculado, { 'pagamento calculado': (x) => x === true });
  if (!calculado) {
    finalizados.add(0);
    return;
  }

  const pago = postJson(`${PAG}/pagamentos/ticket/${id}/pagar`, { metodo: 'PIX' }, { name: 'pagar' });
  check(pago, { 'pagamento aceito (200)': (x) => x.status === 200 });

  finalizados.add(aguardarTicket(id, ['FINALIZADO']) === 'FINALIZADO' ? 1 : 0);
}

export default function () {
  if (FASE === 'entrada') entrar();
  else sair();
}
