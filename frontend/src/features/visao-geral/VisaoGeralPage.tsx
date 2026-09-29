import { Badge } from '../../components/Badge'
import { AvisoProjeto } from '../../components/Estado'
import { Gauge } from '../../components/Gauge'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import type { ScriptExecucao } from '../projetos/api'

// Enquanto não há histórico (etapas 3 e 4: banco + execução), os
// indicadores mostram o estado "sem execuções", como o painel Node fazia
// antes da primeira execução.
const mes = new Date().toLocaleDateString('pt-BR', { month: 'long' })

export function VisaoGeralPage() {
  const { detalhe } = useProjetoAtual()
  const scripts = detalhe.data?.scripts ?? []

  return (
    <>
      <header className="page-head">
        <h1>Visão geral</h1>
        <span className="pill idle">Sem execuções</span>
      </header>
      <AvisoProjeto />

      <section className="grid2" aria-label="Métricas principais">
        <article className="card kpi">
          <span className="eyebrow">Testes no mês</span>
          <div>
            <div className="kpi-value">0</div>
            <div className="kpi-sub">0 execuções em {mes}</div>
          </div>
        </article>
        <article className="card kpi">
          <div className="accent green" />
          <span className="eyebrow">Aprovação</span>
          <div>
            <div className="kpi-value green">—</div>
            <div className="kpi-sub">média do período</div>
          </div>
        </article>
        <article className="card kpi">
          <div className="accent amber" />
          <span className="eyebrow">Falhas na fila</span>
          <div>
            <div className="kpi-value amber">0</div>
            <div className="kpi-sub">triagem em dia</div>
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
            <Gauge pct={null} />
          </div>
        </article>
        <article className="card">
          <span className="eyebrow" style={{ display: 'block', marginBottom: 12 }}>
            Falhas por módulo
          </span>
          <div className="empty">
            <b>Nenhuma falha no mês</b>As falhas aparecem aqui agrupadas pelo spec de origem.
          </div>
        </article>
      </section>

      <section className="rows" aria-label="Execuções rápidas">
        {detalhe.isPending && <div className="card empty">Carregando scripts…</div>}
        {detalhe.data && scripts.length === 0 && (
          <div className="card empty">Nenhum script de execução no package.json.</div>
        )}
        {scripts.map((s) => (
          <LinhaScript key={s.nome} script={s} />
        ))}
      </section>
    </>
  )
}

function LinhaScript({ script }: { script: ScriptExecucao }) {
  const n = script.specs.length
  return (
    <div className="cmd-row">
      <span className="cmd-name">npm run {script.nome}</span>
      <div className="cmd-meta">
        <span className="muted">
          {n} {n === 1 ? 'spec' : 'specs'}
        </span>
        <Badge tom="idle">Nunca executado</Badge>
        <button className="play" disabled aria-label={`Executar ${script.nome}`} title="A execução pelo painel chega na etapa 4">
          <svg viewBox="0 0 24 24">
            <path d="M8 5v14l11-7z" />
          </svg>
        </button>
      </div>
    </div>
  )
}
