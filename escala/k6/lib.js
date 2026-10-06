import http from 'k6/http';
import { sleep } from 'k6';

export const EST = __ENV.EST_URL || 'http://balanceador:8081';
export const VAGAS_URL = __ENV.VAGAS_URL || 'http://balanceador:8082';
export const PAG = __ENV.PAG_URL || 'http://balanceador:8083';
export const RODADA = __ENV.RODADA || '0000';
export const TIMEOUT_S = parseInt(__ENV.TIMEOUT_S || '60');

const CABECALHOS = { 'Content-Type': 'application/json' };

export function postJson(url, corpo, tags) {
  return http.post(url, JSON.stringify(corpo), { headers: CABECALHOS, tags: tags || {} });
}

export function esperarSaude(base) {
  for (let i = 0; i < 60; i++) {
    const r = http.get(`${base}/actuator/health`);
    if (r.status === 200) return;
    sleep(2);
  }
  throw new Error(`Serviço não ficou saudável: ${base}`);
}

function criar(url, corpo) {
  const r = postJson(url, corpo);
  if (r.status !== 201) throw new Error(`POST ${url} -> ${r.status} ${r.body}`);
  return r.json('id');
}

export function prepararVagas(qtd) {
  const tipoId = criar(`${VAGAS_URL}/tipos-vaga`, { nome: `Carro-${RODADA}` });
  const setorId = criar(`${VAGAS_URL}/setores`, { codigo: `S${RODADA}`, status: 'ATIVO' });
  const blocoId = criar(`${VAGAS_URL}/blocos`, { setorId, codigo: `B${RODADA}`, status: 'ATIVO' });

  for (let inicio = 0; inicio < qtd; inicio += 20) {
    const lote = [];
    for (let j = inicio; j < Math.min(inicio + 20, qtd); j++) {
      lote.push([
        'POST',
        `${VAGAS_URL}/vagas`,
        JSON.stringify({ numero: `V${RODADA}-${j}`, blocoId, tipoId }),
        { headers: CABECALHOS },
      ]);
    }
    http.batch(lote).forEach((r) => {
      if (r.status !== 201) throw new Error(`POST /vagas -> ${r.status} ${r.body}`);
    });
  }
}

export function placa(i) {
  return `${RODADA}${i.toString(36).toUpperCase().padStart(5, '0')}`;
}

export function aguardarTicket(id, alvos, timeoutS) {
  const limite = Date.now() + (timeoutS || TIMEOUT_S) * 1000;
  let ultimo = null;
  while (Date.now() < limite) {
    const r = http.get(`${EST}/tickets/${id}`, { tags: { name: 'consulta_ticket' } });
    if (r.status === 200) {
      ultimo = r.json('status');
      if (alvos.includes(ultimo)) return ultimo;
    }
    sleep(0.5);
  }
  return ultimo;
}
