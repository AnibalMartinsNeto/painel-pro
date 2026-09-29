import { Link } from 'react-router'
import { Badge } from '../../components/Badge'
import { fmtData, fmtDuracao } from '../../lib/formato'
import type { ExecucaoResumo, StatusExecucao } from './api'

/** Selo de status de execução, com as mesmas cores do painel Node. */
export function StatusBadge({ status }: { status: StatusExecucao }) {
  const mapa = {
    PASSOU: ['ok', 'Passou'],
    FALHOU: ['bad', 'Falhou'],
    ERRO: ['bad', 'Erro'],
    CANCELADA: ['idle', 'Cancelada'],
    EM_ANDAMENTO: ['warn', 'Executando'],
  } as const
  const [tom, texto] = mapa[status]
  return <Badge tom={tom}>{texto}</Badge>
}

/** Barrinha verde/vermelha/cinza proporcional aos resultados. */
export function MiniBarra({ e }: { e: ExecucaoResumo }) {
  const t = Math.max(1, e.total)
  return (
    <span className="mini-bar" aria-hidden="true">
      <i className="p" style={{ width: `${(e.aprovados / t) * 100}%` }} />
      <i className="f" style={{ width: `${(e.reprovados / t) * 100}%` }} />
      <i className="s" style={{ width: `${(e.pulados / t) * 100}%` }} />
    </span>
  )
}

/** Um item do histórico de execuções. */
export function ItemExecucao({ e }: { e: ExecucaoResumo }) {
  return (
    <Link className="list-item" to={`/execucoes/${e.id}`}>
      <div className="li-main">
        <div className="li-title">{e.script ?? 'Execução personalizada'}</div>
        <div className="li-sub">
          {fmtData(e.iniciadaEm)} · {e.navegador ?? '—'} · {fmtDuracao(e.duracaoMs)}
          {e.importada && ' · importada do painel Node'}
        </div>
      </div>
      {e.total > 0 && (
        <>
          <span className="hint num">
            {e.aprovados}/{e.total}
          </span>
          <MiniBarra e={e} />
        </>
      )}
      <StatusBadge status={e.status} />
    </Link>
  )
}
