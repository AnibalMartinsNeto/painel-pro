import { useState } from 'react'
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
          <span className="eyebrow">Resultado por execução</span>
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

const SEGMENTOS = [
  { campo: 'aprovados', rotulo: 'Passou', cor: 'var(--green)' },
  { campo: 'reprovados', rotulo: 'Falhou', cor: 'var(--red)' },
  { campo: 'pulados', rotulo: 'Pulado', cor: 'var(--faint)' },
] as const

/** Escala "redonda" para o eixo: 7 → 8, 17 → 20, 35 → 40. */
function topoDoEixo(maximo: number) {
  const passo = maximo <= 10 ? 2 : maximo <= 50 ? 10 : 50
  return Math.max(passo, Math.ceil(maximo / passo) * passo)
}

/**
 * Barras empilhadas em SVG: quantos testes passaram, falharam e foram
 * pulados em cada execução. A altura é a CONTAGEM real (uma falha em 1
 * teste não parece igual a 50 falhas), o rótulo em cima é aprovados/total
 * e o mouse mostra o detalhe. Clicar abre a execução.
 */
function GraficoAprovacao({ execucoes }: { execucoes: ExecucaoResumo[] }) {
  const navigate = useNavigate()
  const [foco, setFoco] = useState<number | null>(null)
  const W = 720
  const H = 220
  const pad = { l: 34, r: 8, t: 22, b: 34 }
  const topo = topoDoEixo(Math.max(1, ...execucoes.map((e) => e.total)))
  const largura = (W - pad.l - pad.r) / execucoes.length
  const bw = Math.min(40, largura * 0.62)
  const y = (v: number) => pad.t + (H - pad.t - pad.b) * (1 - v / topo)
  const xCentro = (i: number) => pad.l + i * largura + largura / 2
  const marcas = [0, topo / 2, topo]
  // Com muitas barras, rotula só algumas datas para não encavalar.
  const cadaQuantas = Math.ceil(execucoes.length / 12)

  const avaliados = execucoes.reduce((a, e) => a + e.aprovados + e.reprovados, 0)
  const aprovados = execucoes.reduce((a, e) => a + e.aprovados, 0)
  const comFalha = execucoes.filter((e) => e.reprovados > 0).length
  const emFoco = foco == null ? null : execucoes[foco]

  return (
    <>
      <div className="grafico-resumo">
        <span>
          <b>{avaliados ? `${Math.round((aprovados / avaliados) * 100)}%` : '—'}</b> dos testes passaram
        </span>
        <span>
          <b>{comFalha}</b> de {execucoes.length} execuções com falha
        </span>
        <span className="grafico-legenda" aria-label="Legenda">
          {SEGMENTOS.map((s) => (
            <span key={s.campo}>
              <i style={{ background: s.cor }} /> {s.rotulo}
            </span>
          ))}
        </span>
      </div>
      <div className="grafico-area" onMouseLeave={() => setFoco(null)}>
        <svg className="chart" viewBox={`0 0 ${W} ${H}`} role="img" aria-label="Resultado dos testes por execução">
          {marcas.map((v) => (
            <g key={v}>
              <line className="gridline" x1={pad.l} x2={W - pad.r} y1={y(v)} y2={y(v)} />
              <text x={pad.l - 6} y={y(v) + 3} textAnchor="end">
                {v}
              </text>
            </g>
          ))}
          {execucoes.map((e, i) => {
            let base = 0
            const ativo = foco === i
            return (
              <g
                key={e.id}
                style={{ cursor: 'pointer', opacity: foco == null || ativo ? 1 : 0.45 }}
                onMouseEnter={() => setFoco(i)}
                onClick={() => navigate(`/execucoes/${e.id}`)}
              >
                {/* área de clique da coluna inteira, maior que a barra */}
                <rect x={pad.l + i * largura} y={pad.t} width={largura} height={H - pad.t - pad.b} fill="transparent" />
                {SEGMENTOS.map((s) => {
                  const v = e[s.campo]
                  if (!v) return null
                  const y0 = y(base)
                  base += v
                  const alto = y0 - y(base)
                  return (
                    <rect
                      key={s.campo}
                      x={xCentro(i) - bw / 2}
                      y={y(base)}
                      width={bw}
                      // 2px de respiro entre os segmentos empilhados
                      height={Math.max(1, alto - (base < e.total ? 2 : 0))}
                      rx={2}
                      fill={s.cor}
                    />
                  )
                })}
                <text x={xCentro(i)} y={y(e.total) - 6} textAnchor="middle" className={ativo ? 'forte' : undefined}>
                  {e.aprovados}/{e.total}
                </text>
                {(i % cadaQuantas === 0 || ativo) && (
                  <>
                    <text x={xCentro(i)} y={H - 18} textAnchor="middle" className={ativo ? 'forte' : undefined}>
                      #{e.id}
                    </text>
                    <text x={xCentro(i)} y={H - 5} textAnchor="middle">
                      {new Date(e.iniciadaEm).toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' })}
                    </text>
                  </>
                )}
              </g>
            )
          })}
        </svg>
        {emFoco && foco != null && (
          <div
            className="grafico-dica"
            role="tooltip"
            style={{
              left: `${(xCentro(foco) / W) * 100}%`,
              transform: `translateX(${foco > execucoes.length / 2 ? '-100%' : '0'})`,
            }}
          >
            <b>
              #{emFoco.id} · {emFoco.script ?? 'execução personalizada'}
            </b>
            <span>
              {fmtData(emFoco.iniciadaEm)} · {emFoco.navegador ?? '—'} · {fmtDuracao(emFoco.duracaoMs)}
            </span>
            <span>
              {emFoco.aprovados} passaram · {emFoco.reprovados} falharam · {emFoco.pulados} pulados
            </span>
            <span className="hint">clique para abrir a execução</span>
          </div>
        )}
      </div>
    </>
  )
}
