import { Link, useParams } from 'react-router'
import { Badge } from '../../components/Badge'
import { Carregando, ErroApi } from '../../components/Estado'
import { fmtData, fmtDuracao, plural } from '../../lib/formato'
import { useExecucao, type ResultadoTeste } from './api'
import { StatusBadge } from './componentes'

/** Tela /execucoes/:id — execução com os resultados agrupados por spec. */
export function ExecucaoDetalhePage() {
  const { id = '' } = useParams()
  const { data, isPending, error } = useExecucao(id)

  if (isPending) return <Carregando texto="Carregando execução…" />
  if (error) return <ErroApi erro={error} />

  const { execucao: e, resultados } = data
  // Agrupa os resultados por spec, preservando a ordem de execução.
  const porSpec = new Map<string, ResultadoTeste[]>()
  for (const r of resultados) porSpec.set(r.spec, [...(porSpec.get(r.spec) ?? []), r])

  return (
    <>
      <header className="page-head">
        <div>
          <Link className="btn ghost sm" to="/execucoes">
            ← Execuções
          </Link>
          <h1 style={{ marginTop: 6 }}>{e.script ?? 'Execução personalizada'}</h1>
          <p>
            {fmtData(e.iniciadaEm)} · {e.navegador ?? '—'}
            {data.versaoFerramenta && ` · ${data.versaoFerramenta}`}
            {e.importada && ' · importada do painel Node'}
          </p>
        </div>
        <div className="btn-row">
          <a className="btn sm" href={`/api/execucoes/${e.id}/log.txt`} target="_blank" rel="noopener">
            Ver log completo
          </a>
          <StatusBadge status={e.status} />
        </div>
      </header>

      <div className="stat-strip">
        <div className="stat"><b>{e.total}</b><span>Testes</span></div>
        <div className="stat g"><b>{e.aprovados}</b><span>Passaram</span></div>
        <div className="stat r"><b>{e.reprovados}</b><span>Falharam</span></div>
        <div className="stat a"><b>{e.pulados}</b><span>Pulados</span></div>
        <div className="stat"><b>{fmtDuracao(e.duracaoMs)}</b><span>Duração</span></div>
      </div>

      {data.erro && <div className="err" style={{ margin: 0 }}>{data.erro}</div>}

      <section>
        {[...porSpec].map(([spec, testes]) => (
          <BlocoSpec key={spec} spec={spec} testes={testes} />
        ))}
      </section>
    </>
  )
}

function BlocoSpec({ spec, testes }: { spec: string; testes: ResultadoTeste[] }) {
  const falhas = testes.filter((t) => t.status === 'FALHOU').length
  const aprovados = testes.filter((t) => t.status === 'PASSOU').length
  return (
    // Specs com falha já vêm abertos, como no painel Node.
    <details className="spec" open={falhas > 0}>
      <summary>
        <span className="cmd-name" style={{ flex: 1 }}>{spec}</span>
        <span className="hint num">{aprovados}/{testes.length}</span>
        {falhas ? <Badge tom="bad">{plural(falhas, 'falha', 'falhas')}</Badge> : <Badge tom="ok">OK</Badge>}
      </summary>
      <div className="spec-body">
        {testes.map((t) => (
          <LinhaTeste key={t.id} t={t} />
        ))}
      </div>
    </details>
  )
}

const ICONE = { PASSOU: '✓', FALHOU: '✕', PENDENTE: '–', PULADO: '–' } as const
const CLASSE = { PASSOU: 'passed', FALHOU: 'failed', PENDENTE: 'pending', PULADO: 'skipped' } as const

function LinhaTeste({ t }: { t: ResultadoTeste }) {
  const partes = t.titulo.split(' › ')
  return (
    <div className="test">
      <div className="test-head">
        <span className={`state-icon ${CLASSE[t.status]}`}>{ICONE[t.status]}</span>
        <div className="test-title">
          {partes.slice(0, -1).map((p, i) => (
            <small key={i}>{p} › </small>
          ))}
          {partes.at(-1)}
        </div>
        <span className="test-dur">{fmtDuracao(t.duracaoMs)}</span>
      </div>
      {t.status === 'FALHOU' && (
        <>
          <div className="test-tools">
            {t.tipoErro && <Badge tom="bad">{t.tipoErro}</Badge>}
            <Link className="btn sm green" to={`/triagem/${t.id}`}>
              Triar com IA
            </Link>
          </div>
          {t.mensagemErro && <div className="err">{t.mensagemErro}</div>}
        </>
      )}
    </div>
  )
}
