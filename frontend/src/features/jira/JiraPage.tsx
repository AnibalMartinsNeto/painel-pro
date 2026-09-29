import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Badge } from '../../components/Badge'
import { ErroApi } from '../../components/Estado'
import { fmtData, plural } from '../../lib/formato'
import { useConfiguracoes } from '../configuracoes/api'
import { useEmAndamento, useIniciarExecucao } from '../execucoes/api'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import { useBugsPublicados, useDemanda } from './api'

/** Tela /jira: testes por demanda e bugs publicados pelo painel. */
export function JiraPage() {
  const { id: projeto, detalhe } = useProjetoAtual()
  const { data: config } = useConfiguracoes()
  const [digitada, setDigitada] = useState('')
  const [chave, setChave] = useState('')
  const demanda = useDemanda(projeto, chave)
  const bugs = useBugsPublicados(projeto)
  const { data: emAndamento } = useEmAndamento()
  const executar = useIniciarExecucao()
  const navigate = useNavigate()
  const prefixo = config?.jira.projeto ?? 'DEV'

  const buscar = (e: FormEvent) => {
    e.preventDefault()
    setChave(digitada.trim().toUpperCase())
  }

  const rodarSpecs = (specs: string[]) =>
    executar.mutate(
      {
        projeto,
        script: null,
        specs,
        navegador: detalhe.data?.tipo === 'K6' ? null : (detalhe.data?.navegadores[0] ?? null),
        retentativas: 0,
        abrirNavegador: false,
      },
      { onSuccess: () => navigate('/execucoes') },
    )

  const d = demanda.data
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Jira</h1>
          <p>Busque os testes pela chave da issue e acompanhe os bugs publicados pelo painel.</p>
        </div>
        {config?.jira.configurado ? (
          <span className="pill ok">Projeto {config.jira.projeto}</span>
        ) : (
          <Link className="pill idle" to="/configuracoes">Não configurado · configurar</Link>
        )}
      </header>

      <section className="card">
        <span className="eyebrow" style={{ display: 'block', marginBottom: 12 }}>
          Testes por demanda
        </span>
        <form className="btn-row" onSubmit={buscar}>
          <input
            className="input"
            style={{ maxWidth: 200 }}
            placeholder={`${prefixo}-1`}
            aria-label="Chave da issue"
            value={digitada}
            onChange={(e) => setDigitada(e.target.value)}
          />
          <button className="btn primary" type="submit" disabled={!digitada.trim()}>
            Buscar
          </button>
        </form>
        <p className="hint" style={{ margin: '10px 0 0' }}>
          O painel procura a chave (ex.: <code>{prefixo}-1</code>) no código dos specs de <b>{detalhe.data?.nome ?? projeto}</b>. Coloque-a no título do
          teste, por exemplo <code>describe("Login [{prefixo}-1]")</code>.
        </p>

        {demanda.isFetching && <div className="empty">Buscando {chave}…</div>}
        {demanda.error && <div style={{ marginTop: 14 }}><ErroApi erro={demanda.error} /></div>}
        {d && !demanda.isFetching && (
          <div style={{ marginTop: 14 }}>
            {d.issue ? (
              <div className="list-item" style={{ cursor: 'default', padding: '12px 0' }}>
                <Badge tom="info">{d.issue.tipo} · {d.issue.chave}</Badge>
                <div className="li-main">
                  <div className="li-title">{d.issue.resumo}</div>
                  <div className="li-sub">{d.issue.status}</div>
                </div>
                <a className="btn ghost sm" href={d.issue.url} target="_blank" rel="noopener">abrir no Jira ↗</a>
              </div>
            ) : (
              <p className="hint" role="alert">Não foi possível consultar {d.chave} no Jira: {d.erro}</p>
            )}
            {d.specs.length ? (
              <>
                <div className="spec-list" style={{ margin: '10px 0' }} aria-label="Specs da demanda">
                  {d.specs.map((s) => (
                    <span key={s} className="check">{s}</span>
                  ))}
                </div>
                <button className="btn primary" type="button" onClick={() => rodarSpecs(d.specs)} disabled={!!emAndamento || executar.isPending}>
                  Executar {plural(d.specs.length, 'spec', 'specs')}
                </button>
                {executar.error && <span className="hint" role="alert" style={{ color: 'var(--red)', marginLeft: 8 }}>{executar.error.message}</span>}
              </>
            ) : (
              <div className="empty">
                <b>Nenhum spec de {detalhe.data?.nome} cita {d.chave}</b>
                Coloque <code>{d.chave}</code> no título do teste para vinculá-lo.
              </div>
            )}
          </div>
        )}
      </section>

      <section className="card flat">
        <div className="card-head" style={{ padding: '16px 16px 0' }}>
          <span className="eyebrow">Bugs publicados</span>
          <span className="hint">{bugs.data?.length ?? 0}</span>
        </div>
        <div className="list" style={{ marginTop: 8 }}>
          {bugs.data?.length === 0 && (
            <div className="empty">
              Nenhum bug publicado ainda. Use <Link to="/triagem">Triagem IA</Link> → Publicar no Jira.
            </div>
          )}
          {bugs.data?.map((b) => (
            <a key={b.chave} className="list-item" href={b.url} target="_blank" rel="noopener">
              <Badge tom="bad">{b.chave}</Badge>
              <div className="li-main">
                <div className="li-title">{b.titulo}</div>
                <div className="li-sub">
                  {fmtData(b.publicadaEm)}
                  {b.demanda && ` · ligado a ${b.demanda}`} · {b.chaveTeste.split(' › ').slice(-2).join(' › ')}
                </div>
              </div>
              <span className="hint">↗</span>
            </a>
          ))}
        </div>
      </section>
    </>
  )
}
