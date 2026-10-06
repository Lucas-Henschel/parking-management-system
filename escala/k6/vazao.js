import exec from 'k6/execution';
import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';
import {
  EST, PAG, VAGAS_URL, esperarSaude, postJson, aguardarTicket, placa,
} from './lib.js';

const PASSOS = (__ENV.PASSOS || '50,100,200,400').split(',').map((p) => parseInt(p));
const DURACAO_S = parseInt(__ENV.DURACAO_S || '20');
const AMOSTRA_A_CADA = 20;

const tempoAteAtivo = new Trend('tempo_ate_ativo', true);
const amostrasSemAtivo = new Counter('amostras_sem_ativo');
const TIMEOUT_AMOSTRA_S = 120;

export const options = {
  scenarios: {
    rampa: {
      executor: 'ramping-arrival-rate',
      startRate: PASSOS[0],
      timeUnit: '1s',
      preAllocatedVUs: 200,
      maxVUs: 1000,
      gracefulStop: '130s',
      stages: PASSOS.map((alvo) => ({ target: alvo, duration: `${DURACAO_S}s` })),
    },
  },
  summaryTrendStats: ['avg', 'med', 'p(90)', 'p(95)', 'max'],
  // Estes limiares sempre passam: existem só para o k6 incluir as métricas
  // filtradas por tag no --summary-export.
  thresholds: {
    'http_req_duration{name:entrada}': ['max>=0'],
    'http_reqs{name:entrada}': ['count>=0'],
    tempo_ate_ativo: ['max>=0'],
    amostras_sem_ativo: ['count>=0'],
  },
};

export function setup() {
  esperarSaude(EST);
  esperarSaude(VAGAS_URL);
  esperarSaude(PAG);
}

export default function () {
  const i = exec.scenario.iterationInTest;
  const r = postJson(`${EST}/entrada`, { placa: placa(i) }, { name: 'entrada' });
  const aceita = check(r, { 'entrada aceita (202)': (x) => x.status === 202 });

  if (aceita && i % AMOSTRA_A_CADA === 0) {
    const inicio = Date.now();
    if (aguardarTicket(r.json('id'), ['ATIVO', 'RECUSADO'], TIMEOUT_AMOSTRA_S) === 'ATIVO') {
      tempoAteAtivo.add(Date.now() - inicio);
      amostrasSemAtivo.add(0);
    } else {
      amostrasSemAtivo.add(1);  // RECUSADO ou timeout
    }
  }
}
