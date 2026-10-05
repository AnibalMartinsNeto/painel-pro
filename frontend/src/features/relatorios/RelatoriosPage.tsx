import { Link, useNavigate } from 'react-router'
import { Badge } from '../../components/Badge'
import { AvisoProjeto, ErroApi } from '../../components/Estado'
import { fmtData, fmtDuracao } from '../../lib/formato'
import { ItemExecucao } from '../execucoes/componentes'
import { useExecucoes, type ExecucaoResumo } from '../execucoes/api'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import { urlCsv, useRelatorio, type TesteComFalha } from './api'

// Os números vêm de GET /api/relatorios — todo o histórico do projeto
// gravado no PostgreSQL (a Visão geral mostra só o mês corrente).
export function RelatoriosPage() {
  const { id: projeto } = useProjetoAtual()
  const relatorio = useRelatorio(projeto)
  const execucoes = useExecucoes(projeto)
  const r = relatorio.data

  const exportarFalhas = () => {
    if (!r) return
    const conteudo = JSON.stringify(r.testesComFalha, null, 2)
    const a = document.createElement('a')
    a.href = URL.createObjectURL(new Blob([conteudo], { type: 'application/json' }))
    a.download = `qa-panel-${projeto}-falhas-${new Date().toISOString().slice(0, 10)}.json`
    a.click()
    URL.revokeObjectURL(a.href)
  }

  const stats: [string, string | number, string][] = [
    ['Execuções', r?.execucoes ?? 0, ''],
    ['Testes únicos', r?.testesUnicos ?? 0, ''],
    ['Já falharam', r?.jaFalharam ?? 0, 'r'],
    ['Instáveis', r?.instaveis ?? 0, 'a'],
    ['Tempo total', r ? fmtDuracao(r.tempoTotalMs) : '—', ''],
  ]

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Relatórios</h1>
          <p>Histórico completo das execuções registradas pelo painel.</p>
        </div>
        <div className="btn-row">
          {r && r.execucoes > 0 ? (
            <a className="btn sm" href={urlCsv(projeto)} download>
              Exportar CSV
            </a>
          ) : (
            <button className="btn sm" disabled>
              Exportar CSV
            </button>
          )}
          <button className="btn sm" onClick={exportarFalhas} disabled={!r?.testesComFalha.length}>
            Exportar falhas (JSON)
          </button>
        </div>
      </header>
      <AvisoProjeto />
      {relatorio.error && <ErroApi erro={relatorio.error} />}

      <div className="stat-strip" aria-label="Totais">
        {stats.map(([rotulo, valor, cor]) => (
          <div key={rotulo} className={`stat ${cor}`}>
            <b>{valor}</b>
            <span>{rotulo}</span>
          </div>
        ))}
      </div>

      <section className="card">
        <div className="card-head">
          <span className="eyebrow">Aprovação por execução</span>
          {!!r?.aprovacaoPorExecucao.length && <span className="hint">últimas {r.aprovacaoPorExecucao.length}</span>}
        </div>
        {r?.aprovacaoPorExecucao.length ? (
          <GraficoAprovacao execucoes={r.aprovacaoPorExecucao} />
        ) : (
          <div className="empty">{relatorio.isPending ? 'Carregando…' : 'Sem execuções concluídas.'}</div>
        )}
      </section>

      <section className="card flat">
        <div className="card-head" style={{ padding: '16px 16px 0' }}>
          <span className="eyebrow">Testes que mais falham</span>
          {!!r?.testesComFalha.length && <span className="hint">{r.testesComFalha.length}</span>}
        </div>
        {r?.testesComFalha.length ? (
          <TabelaFalhas testes={r.testesComFalha.slice(0, 20)} />
        ) : (
          <div className="empty">Nenhum teste falhou até agora.</div>
        )}
      </section>

      <section className="card flat">
        <div className="card-head" style={{ padding: '16px 16px 0' }}>
          <span className="eyebrow">Execuções recentes</span>
          {!!execucoes.data?.length && <span className="hint">últimas {execucoes.data.length} · todas no CSV</span>}
        </div>
        <div className="list" style={{ marginTop: 8 }}>
          {execucoes.data?.length === 0 && <div className="empty">Nenhuma execução.</div>}
          {execucoes.data?.map((e) => <ItemExecucao key={e.id} e={e} />)}
        </div>
      </section>
    </>
  )
}

function TabelaFalhas({ testes }: { testes: TesteComFalha[] }) {
  return (
    <div className="tbl-wrap">
      <table className="tbl">
        <thead>
          <tr>
            <th>Teste</th>
            <th>Módulo</th>
            <th>Tipo</th>
            <th className="num">Falhas</th>
            <th className="num">Taxa</th>
            <th>Última falha</th>
          </tr>
        </thead>
        <tbody>
          {testes.map((t) => (
            <tr key={t.chave}>
              <td>
                {t.ultimoResultadoId ? (
                  <Link to={`/triagem/${t.ultimoResultadoId}`} style={{ textDecoration: 'none' }}>
                    {t.titulo.split(' › ').at(-1)}
                  </Link>
                ) : (
                  t.titulo.split(' › ').at(-1)
                )}
                {t.instavel && (
                  <>
                    {' '}
                    <Badge tom="warn">instável</Badge>
                  </>
                )}
                <div className="hint">{t.spec}</div>
              </td>
              <td>{t.modulo}</td>
              <td className="hint">{t.tipoErro ?? '—'}</td>
              <td className="num">
                {t.falhas}/{t.execucoes}
              </td>
              <td className="num">{Math.round((t.falhas / Math.max(1, t.execucoes)) * 100)}%</td>
              <td className="hint">{fmtData(t.ultimaFalha)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

/** Barras em SVG: % de aprovação de cada execução; clicar abre a execução. */
function GraficoAprovacao({ execucoes }: { execucoes: ExecucaoResumo[] }) {
  const navigate = useNavigate()
  const W = 700
  const H = 180
  const pad = { l: 34, r: 8, t: 10, b: 22 }
  const bw = Math.min(56, (W - pad.l - pad.r) / execucoes.length)
  const y = (v: number) => pad.t + (H - pad.t - pad.b) * (1 - v / 100)

  return (
    <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Aprovação por execução">
      {[0, 50, 100].map((v) => (
        <g key={v}>
          <line className="gridline" x1={pad.l} x2={W - pad.r} y1={y(v)} y2={y(v)} />
          <text x={pad.l - 6} y={y(v) + 3} textAnchor="end">
            {v}%
          </text>
        </g>
      ))}
      {execucoes.map((e, i) => {
        const avaliados = e.aprovados + e.reprovados
        const pct = avaliados ? (e.aprovados / avaliados) * 100 : 0
        const cor = e.status === 'PASSOU' ? 'var(--green)' : pct >= 70 ? 'var(--amber)' : 'var(--red)'
        return (
          <g key={e.id}>
            <rect
              x={pad.l + i * bw + bw * 0.18}
              y={y(pct)}
              width={Math.max(2, bw * 0.64)}
              height={Math.max(1, y(0) - y(pct))}
              rx={3}
              fill={cor}
              style={{ cursor: 'pointer' }}
              onClick={() => navigate(`/execucoes/${e.id}`)}
            >
              <title>{`#${e.id} · ${fmtData(e.iniciadaEm)} — ${Math.round(pct)}% (${e.aprovados}/${avaliados})`}</title>
            </rect>
            {execucoes.length <= 12 && (
              <text x={pad.l + i * bw + bw / 2} y={H - 6} textAnchor="middle">
                {new Date(e.iniciadaEm).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}
              </text>
            )}
          </g>
        )
      })}
    </svg>
  )
}
