import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { Badge } from '../../components/Badge'
import { ErroApi } from '../../components/Estado'
import { fmtData, plural } from '../../lib/formato'
import { useConfiguracoes } from '../configuracoes/api'
import { useEmAndamento, useIniciarExecucao } from '../execucoes/api'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import { useBugsPublicados, useDemanda, useHistoricoJira } from './api'

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
            {d.origem && (
              <div className="note" style={{ margin: '10px 0' }} aria-label="Origem do bug">
                <b>{d.chave} foi publicado pelo painel</b> a partir da falha do teste
                <div style={{ margin: '6px 0' }}>
                  <code>{d.origem.teste}</code>
                  <div className="hint">
                    {d.origem.spec}
                    {d.origem.publicadaEm && ` · publicado em ${fmtData(d.origem.publicadaEm)}`}
                  </div>
                </div>
                <div className="btn-row">
                  {d.origem.specExiste ? (
                    <button
                      className="btn primary sm"
                      type="button"
                      onClick={() => rodarSpecs([d.origem!.spec])}
                      disabled={!!emAndamento || executar.isPending}
                    >
                      Retestar
                    </button>
                  ) : (
                    <span className="hint">O arquivo deste teste não existe mais no projeto: não dá para retestar.</span>
                  )}
                  {d.origem.demanda && (
                    <button
                      className="btn sm"
                      type="button"
                      onClick={() => {
                        setDigitada(d.origem!.demanda!)
                        setChave(d.origem!.demanda!)
                      }}
                    >
                      Ver testes da demanda {d.origem.demanda}
                    </button>
                  )}
                </div>
              </div>
            )}
            {d.origem && !d.specs.length ? null : d.specs.length ? (
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

      <HistoricoJira configurado={!!config?.jira.configurado} projetoJira={config?.jira.projeto ?? null} />
    </>
  )
}

const TOM_STATUS = { new: 'idle', indeterminate: 'warn', done: 'ok' } as const

/** Últimas issues do projeto no Jira, de qualquer origem (painel, portal, criadas à mão). */
function HistoricoJira({ configurado, projetoJira }: { configurado: boolean; projetoJira: string | null }) {
  const historico = useHistoricoJira(configurado)
  const [soDoPainel, setSoDoPainel] = useState(false)
  const issues = (historico.data ?? []).filter((i) => !soDoPainel || i.doPainel)

  return (
    <section className="card flat">
      <div className="card-head" style={{ padding: '16px 16px 0' }}>
        <span className="eyebrow">Histórico do projeto {projetoJira ?? ''} no Jira</span>
        {configurado && (
          <div className="btn-row">
            <label className="hint" style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>
              <input type="checkbox" checked={soDoPainel} onChange={(e) => setSoDoPainel(e.target.checked)} />
              só criadas pelo painel
            </label>
            <button className="btn ghost sm" type="button" onClick={() => historico.refetch()} disabled={historico.isFetching}>
              {historico.isFetching ? 'Atualizando…' : 'Atualizar'}
            </button>
          </div>
        )}
      </div>
      {!configurado && (
        <div className="empty">
          Configure o Jira em <Link to="/configuracoes">Configurações</Link> para ver o histórico.
        </div>
      )}
      {historico.error && (
        <div style={{ padding: 16 }}>
          <ErroApi erro={historico.error} />
        </div>
      )}
      {historico.isPending && configurado && <div className="empty">Buscando no Jira…</div>}
      <div className="list" style={{ marginTop: 8 }} aria-label="Histórico do Jira">
        {historico.data && issues.length === 0 && (
          <div className="empty">{soDoPainel ? 'Nenhuma issue criada pelo painel.' : 'Nenhuma issue no projeto.'}</div>
        )}
        {issues.map((i) => (
          <a key={i.chave} className="list-item" href={i.url} target="_blank" rel="noopener">
            <Badge tom="info">{i.chave}</Badge>
            <div className="li-main">
              <div className="li-title">{i.resumo}</div>
              <div className="li-sub">
                {i.tipo ?? '—'} · {fmtData(i.criadaEm)}
                {i.prioridade && ` · prioridade ${i.prioridade}`}
                {i.doPainel && ' · criada pelo painel'}
              </div>
            </div>
            {i.status && <Badge tom={TOM_STATUS[i.categoriaStatus ?? 'new'] ?? 'idle'}>{i.status}</Badge>}
            <span className="hint">↗</span>
          </a>
        ))}
      </div>
    </section>
  )
}
