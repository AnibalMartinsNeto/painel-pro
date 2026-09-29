import { Link } from 'react-router'
import { Badge } from '../../components/Badge'
import { AvisoProjeto } from '../../components/Estado'
import { Gauge } from '../../components/Gauge'
import { fmtDuracao, fmtPct, plural } from '../../lib/formato'
import { useResumo, type ExecucaoResumo, type ResumoProjeto } from '../execucoes/api'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import type { ScriptExecucao } from '../projetos/api'

// Os números vêm de GET /api/execucoes/resumo — agregados pelo PostgreSQL
// (SUM/COUNT) a partir das execuções gravadas.
export function VisaoGeralPage() {
  const { id, detalhe } = useProjetoAtual()
  const { data: resumo } = useResumo(id)
  const scripts = detalhe.data?.scripts ?? []

  const mes = resumo ? new Date(`${resumo.mes}-02`).toLocaleDateString('pt-BR', { month: 'long' }) : ''
  const corAprovacao = resumo?.aprovacao == null || resumo.aprovacao >= 90 ? 'green' : 'amber'

  return (
    <>
      <header className="page-head">
        <h1>Visão geral</h1>
        <PillSuite ultima={resumo?.ultimaExecucao ?? null} />
      </header>
      <AvisoProjeto />

      <section className="grid2" aria-label="Métricas principais">
        <article className="card kpi">
          <span className="eyebrow">Testes no mês</span>
          <div>
            <div className="kpi-value">{(resumo?.testes ?? 0).toLocaleString('pt-BR')}</div>
            <div className="kpi-sub">
              {plural(resumo?.execucoes ?? 0, 'execução', 'execuções')} em {mes}
            </div>
          </div>
        </article>
        <article className="card kpi">
          <div className="accent green" />
          <span className="eyebrow">Aprovação</span>
          <div>
            <div className={`kpi-value ${corAprovacao}`}>{fmtPct(resumo?.aprovacao)}</div>
            <div className="kpi-sub">média do período</div>
          </div>
        </article>
        <article className="card kpi">
          <div className="accent amber" />
          <span className="eyebrow">Falhas no mês</span>
          <div>
            <div className="kpi-value amber">{resumo?.reprovados ?? 0}</div>
            <div className="kpi-sub">triagem chega na etapa 6</div>
          </div>
        </article>
        <article className="card kpi">
          <div className="accent gray" />
          <span className="eyebrow">Itens no Azure</span>
          <div>
            <div className="kpi-value">0</div>
            <div className="kpi-sub">bugs publicados pelo painel</div>
          </div>
        </article>
      </section>

      <section className="grid-12" aria-label="Detalhamento">
        <article className="card" style={{ display: 'flex', flexDirection: 'column' }}>
          <span className="eyebrow">Aprovação</span>
          <div className="gauge-wrap">
            <Gauge pct={resumo?.aprovacao ?? null} />
          </div>
        </article>
        <article className="card">
          <span className="eyebrow" style={{ display: 'block', marginBottom: 12 }}>
            Falhas por módulo
          </span>
          <FalhasPorModulo resumo={resumo} />
        </article>
      </section>

      <section className="rows" aria-label="Execuções rápidas">
        {detalhe.isPending && <div className="card empty">Carregando scripts…</div>}
        {detalhe.data && scripts.length === 0 && <div className="card empty">Nenhum script de execução no package.json.</div>}
        {scripts.map((s) => (
          <LinhaScript key={s.nome} script={s} ultima={resumo?.ultimaPorScript[s.nome]} />
        ))}
      </section>
    </>
  )
}

function PillSuite({ ultima }: { ultima: ExecucaoResumo | null }) {
  if (!ultima) return <span className="pill idle">Sem execuções</span>
  return ultima.status === 'PASSOU' ? <span className="pill ok">Suíte estável</span> : <span className="pill warn">Suíte instável</span>
}

function FalhasPorModulo({ resumo }: { resumo?: ResumoProjeto }) {
  if (!resumo?.falhasPorModulo.length)
    return (
      <div className="empty">
        <b>Nenhuma falha no mês</b>As falhas aparecem aqui agrupadas pelo spec de origem.
      </div>
    )
  return (
    <div className="bars">
      {resumo.falhasPorModulo.slice(0, 6).map((m) => (
        <div key={m.modulo} className="bar-row">
          <div className="bar-label">
            <span>{m.modulo}</span>
            <span>
              {m.percentual}% · {m.falhas}
            </span>
          </div>
          <div className="bar-track">
            <div className="bar-fill" style={{ width: `${m.percentual}%` }} />
          </div>
        </div>
      ))}
    </div>
  )
}

function LinhaScript({ script, ultima }: { script: ScriptExecucao; ultima?: ExecucaoResumo }) {
  const n = script.specs.length
  const badge = !ultima ? (
    <Badge tom="idle">Nunca executado</Badge>
  ) : ultima.status === 'PASSOU' ? (
    <Badge tom="ok">Passou</Badge>
  ) : ultima.status === 'FALHOU' ? (
    <Badge tom="warn">{plural(ultima.reprovados, 'falha', 'falhas')}</Badge>
  ) : (
    <Badge tom="bad">Erro</Badge>
  )
  return (
    <div className="cmd-row">
      <span className="cmd-name">npm run {script.nome}</span>
      <div className="cmd-meta">
        <span className="muted">
          {plural(n, 'spec', 'specs')}
          {ultima && ` · ${fmtDuracao(ultima.duracaoMs)}`}
        </span>
        {ultima && (
          <Link className="btn ghost sm" to={`/execucoes/${ultima.id}`}>
            ver
          </Link>
        )}
        {badge}
        <button className="play" disabled aria-label={`Executar ${script.nome}`} title="A execução pelo painel chega na etapa 4">
          <svg viewBox="0 0 24 24">
            <path d="M8 5v14l11-7z" />
          </svg>
        </button>
      </div>
    </div>
  )
}
