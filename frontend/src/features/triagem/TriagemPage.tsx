import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { Badge } from '../../components/Badge'
import { Carregando, ErroApi } from '../../components/Estado'
import { fmtData } from '../../lib/formato'
import { useConfiguracoes } from '../configuracoes/api'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import {
  CLASSIFICACOES,
  SEVERIDADES,
  pendente,
  useAnalisar,
  useFilaTriagem,
  usePublicarNoJira,
  useSalvarTriagem,
  type Classificacao,
  type FalhaTriagem,
  type Severidade,
} from './api'

/** Tela /triagem e /triagem/:resultadoId — fila à esquerda, detalhe à direita. */
export function TriagemPage() {
  const { id: projeto } = useProjetoAtual()
  const { resultadoId } = useParams()
  const navigate = useNavigate()
  const { data: config } = useConfiguracoes()
  const { data: fila, isPending, error } = useFilaTriagem(projeto)
  const [filtro, setFiltro] = useState<'pendentes' | 'todas'>('pendentes')

  const pendentes = fila?.filter(pendente) ?? []
  const lista = filtro === 'pendentes' ? pendentes : (fila ?? [])
  // Com um id na URL, mostra exatamente aquela falha; se ela não estiver na
  // fila deste projeto, avisa em vez de abrir outra falha sem dizer nada.
  const pedida = resultadoId ? fila?.find((f) => String(f.resultadoId) === resultadoId) : undefined
  const selecionada = resultadoId ? pedida : lista[0]
  const naoEncontrada = !!resultadoId && !!fila && !pedida

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Triagem IA</h1>
          <p>Testes que falham na execução mais recente. A IA lê o erro e o código do spec e sugere o bug.</p>
        </div>
        {config?.ia.ativa ? (
          <span className="pill ok">IA ativa · {config.ia.provedor === 'gemini' ? config.ia.modeloGemini : config.ia.modeloAnthropic}</span>
        ) : (
          <Link className="pill warn" to="/configuracoes">Sem chave · modo heurístico</Link>
        )}
      </header>

      {isPending && <Carregando />}
      {error && <ErroApi erro={error} />}
      {fila?.length === 0 && (
        <div className="card empty">
          <b>Nenhuma falha em aberto</b>Todos os testes deste projeto passaram na execução mais recente.
        </div>
      )}

      {fila && fila.length > 0 && (
        <div className="split">
          <section className="card flat" aria-label="Fila de falhas">
            <div className="card-head" style={{ padding: '12px 12px 0' }}>
              <div className="seg">
                <button type="button" className={filtro === 'pendentes' ? 'on' : undefined} onClick={() => setFiltro('pendentes')}>
                  Pendentes ({pendentes.length})
                </button>
                <button type="button" className={filtro === 'todas' ? 'on' : undefined} onClick={() => setFiltro('todas')}>
                  Todas ({fila.length})
                </button>
              </div>
            </div>
            <div className="list" style={{ marginTop: 8, maxHeight: 640, overflow: 'auto' }}>
              {lista.length === 0 && <div className="empty">Fila vazia: todas as falhas já foram triadas.</div>}
              {lista.map((f) => (
                <button
                  key={f.resultadoId}
                  type="button"
                  className={`list-item ${f.resultadoId === selecionada?.resultadoId ? 'sel' : ''}`}
                  style={{ textAlign: 'left', background: 'none', border: 0, borderTop: '1px solid var(--card-border)', width: '100%', color: 'inherit' }}
                  onClick={() => navigate(`/triagem/${f.resultadoId}`)}
                >
                  <div className="li-main">
                    <div className="li-title">{f.titulo.split(' › ').slice(-2).join(' › ')}</div>
                    <div className="li-sub">
                      {f.spec.split('/').pop()} · {f.tipoErro ?? 'erro'} · {fmtData(f.ocorridaEm)}
                    </div>
                  </div>
                  {f.triagem?.classificacao ? (
                    <Badge tom={CLASSIFICACOES[f.triagem.classificacao].tom}>Triado</Badge>
                  ) : (
                    <Badge tom="warn">Pendente</Badge>
                  )}
                </button>
              ))}
            </div>
          </section>
          {naoEncontrada && (
            <div className="card empty" role="alert">
              <b>Falha #{resultadoId} fora da fila deste projeto</b>
              Ela pode ser de outro projeto (troque no seletor da barra lateral) ou o teste já voltou a passar.
              <Link className="btn sm" to="/triagem" style={{ marginTop: 8 }}>Ver a fila</Link>
            </div>
          )}
          {/* key: ao trocar de falha, o formulário recomeça com os dados dela */}
          {selecionada && <Detalhe key={`${selecionada.resultadoId}-${selecionada.triagem?.atualizadaEm}`} falha={selecionada} projeto={projeto} iaAtiva={!!config?.ia.ativa} />}
        </div>
      )}
    </>
  )
}

function Detalhe({ falha, projeto, iaAtiva }: { falha: FalhaTriagem; projeto: string; iaAtiva: boolean }) {
  const t = falha.triagem
  const analisar = useAnalisar(projeto)
  const salvar = useSalvarTriagem(projeto)
  const publicar = usePublicarNoJira(projeto)
  const { data: config } = useConfiguracoes()
  const jiraConfigurado = !!config?.jira.configurado
  // Sugere a demanda a partir do título do teste: "Login [DEV-1] › ..." → DEV-1.
  const [demanda, setDemanda] = useState(() => falha.titulo.match(/\b[A-Z][A-Z0-9]+-\d+\b/)?.[0] ?? '')
  const [classificacao, setClassificacao] = useState<Classificacao | ''>(t?.classificacao ?? t?.classificacaoSugerida ?? '')
  const [severidade, setSeveridade] = useState<Severidade | ''>(t?.severidade ?? '')
  const [titulo, setTitulo] = useState(t?.titulo ?? '')
  const [esperado, setEsperado] = useState(t?.esperado ?? '')
  const [encontrado, setEncontrado] = useState(t?.encontrado ?? '')
  const [passos, setPassos] = useState((t?.passos ?? []).join('\n'))
  const [observacoes, setObservacoes] = useState(t?.observacoes ?? '')

  const revisao = (c: Classificacao | '' = classificacao) => ({
    classificacao: c || null,
    severidade: severidade || null,
    titulo,
    esperado,
    encontrado,
    passos: passos.split('\n').map((p) => p.trim()).filter(Boolean),
    observacoes,
  })

  const gravar = (c: Classificacao | '' = classificacao) => salvar.mutate({ resultadoId: falha.resultadoId, revisao: revisao(c) })

  // Publica o que está NA TELA: salva a revisão primeiro e só então cria o bug.
  const publicarNoJira = async () => {
    try {
      await salvar.mutateAsync({ resultadoId: falha.resultadoId, revisao: revisao() })
    } catch {
      return // o erro do salvamento já aparece na tela
    }
    publicar.mutate({ resultadoId: falha.resultadoId, demanda: demanda.trim() })
  }

  const temRascunho = !!t?.titulo
  return (
    <div className="stack">
      <article className="card">
        <div className="card-head">
          <span className="eyebrow">Falha</span>
          <div className="btn-row">
            {falha.tipoErro && <Badge tom="bad">{falha.tipoErro}</Badge>}
            {t?.classificacao && <Badge tom={CLASSIFICACOES[t.classificacao].tom}>{CLASSIFICACOES[t.classificacao].rotulo}</Badge>}
          </div>
        </div>
        <div className="test-title" style={{ fontSize: 14, marginBottom: 10 }}>{falha.titulo}</div>
        <dl className="kv">
          <dt>Spec</dt>
          <dd><code>{falha.spec}</code></dd>
          <dt>Execução</dt>
          <dd>
            <Link to={`/execucoes/${falha.execucaoId}`}>#{falha.execucaoId}</Link> · {fmtData(falha.ocorridaEm)} · {falha.navegador ?? '—'}
          </dd>
        </dl>
        {falha.mensagemErro && <div className="err" style={{ marginLeft: 0 }}>{falha.mensagemErro}</div>}
      </article>

      <article className="card ai-box">
        <div className="card-head">
          <span className="eyebrow" style={{ color: 'var(--green)' }}>Bug sugerido</span>
          <button className="btn green sm" type="button" onClick={() => analisar.mutate(falha.resultadoId)} disabled={analisar.isPending}>
            {analisar.isPending ? (
              <>
                <span className="spinner" aria-hidden="true" /> Analisando…
              </>
            ) : temRascunho ? 'Gerar novamente' : iaAtiva ? 'Analisar com IA' : 'Gerar rascunho'}
          </button>
        </div>
        {analisar.error && (
          <div className="note" role="alert" style={{ color: 'var(--red)', marginBottom: 12 }}>
            {analisar.error.message}
          </div>
        )}

        {!temRascunho ? (
          <div className="empty">
            Clique em <b style={{ display: 'inline' }}>{iaAtiva ? 'Analisar com IA' : 'Gerar rascunho'}</b> para preencher título, esperado, encontrado e passos.
            <div className="btn-row" style={{ justifyContent: 'center', marginTop: 10 }}>
              Ou classifique direto:
              {(Object.keys(CLASSIFICACOES) as Classificacao[]).map((c) => (
                <button key={c} type="button" className="btn sm" onClick={() => gravar(c)} disabled={salvar.isPending}>
                  {CLASSIFICACOES[c].rotulo}
                </button>
              ))}
            </div>
          </div>
        ) : (
          <>
            {t?.origemRascunho === 'HEURISTICA' && (
              <div className="note" style={{ marginBottom: 12 }}>
                Rascunho gerado <b>sem IA</b>, a partir da mensagem de erro. Configure uma chave em <Link to="/configuracoes">Configurações</Link> para uma análise completa.
              </div>
            )}
            <div className="form-grid">
              <div className="field full">
                <label htmlFor="tTitulo">Título</label>
                <input id="tTitulo" className="input" value={titulo} onChange={(e) => setTitulo(e.target.value)} />
              </div>
              <div className="field">
                <label htmlFor="tClass">
                  Classificação
                  {t?.classificacaoSugerida && !t.classificacao && (
                    <span className="hint"> (sugestão da IA: {CLASSIFICACOES[t.classificacaoSugerida].rotulo})</span>
                  )}
                </label>
                <select id="tClass" className="input" value={classificacao} onChange={(e) => setClassificacao(e.target.value as Classificacao | '')}>
                  <option value="">Pendente</option>
                  {(Object.keys(CLASSIFICACOES) as Classificacao[]).map((c) => (
                    <option key={c} value={c}>{CLASSIFICACOES[c].rotulo}</option>
                  ))}
                </select>
              </div>
              <div className="field">
                <label htmlFor="tSev">Severidade</label>
                <select id="tSev" className="input" value={severidade} onChange={(e) => setSeveridade(e.target.value as Severidade | '')}>
                  <option value="">—</option>
                  {(Object.keys(SEVERIDADES) as Severidade[]).map((s) => (
                    <option key={s} value={s}>{SEVERIDADES[s]}</option>
                  ))}
                </select>
              </div>
              <div className="field full">
                <label htmlFor="tEsp">Resultado esperado</label>
                <textarea id="tEsp" className="input" value={esperado} onChange={(e) => setEsperado(e.target.value)} />
              </div>
              <div className="field full">
                <label htmlFor="tEnc">Resultado encontrado</label>
                <textarea id="tEnc" className="input" value={encontrado} onChange={(e) => setEncontrado(e.target.value)} />
              </div>
              <div className="field full">
                <label htmlFor="tPassos">
                  Passos para reproduzir <span className="hint">(um por linha)</span>
                </label>
                <textarea id="tPassos" className="input" rows={5} value={passos} onChange={(e) => setPassos(e.target.value)} />
              </div>
              {t?.analise && (
                <div className="field full">
                  <label>Análise {t.modelo && <span className="hint">({t.modelo})</span>}</label>
                  <div className="note" style={{ color: 'var(--text)' }}>{t.analise}</div>
                </div>
              )}
              <div className="field full">
                <label htmlFor="tObs">Observações internas</label>
                <textarea id="tObs" className="input" rows={2} value={observacoes} onChange={(e) => setObservacoes(e.target.value)} />
              </div>
            </div>
            <div className="btn-row" style={{ marginTop: 14 }}>
              <button className="btn primary" type="button" onClick={() => gravar()} disabled={salvar.isPending}>
                {salvar.isPending ? 'Salvando…' : 'Salvar triagem'}
              </button>
              {salvar.isSuccess && !publicar.isPending && <span className="hint" role="status">Triagem salva.</span>}
              {salvar.error && <span className="hint" role="alert" style={{ color: 'var(--red)' }}>{salvar.error.message}</span>}
            </div>
          </>
        )}
      </article>

      {temRascunho && (
        <article className="card" aria-label="Publicação no Jira">
          <div className="card-head">
            <span className="eyebrow">Jira</span>
            {t?.jiraIssue && <Badge tom="bad">Bug publicado</Badge>}
          </div>
          {t?.jiraIssue ? (
            <div className="btn-row">
              <a className="btn green" href={t.jiraUrl ?? '#'} target="_blank" rel="noopener">
                {t.jiraIssue} no Jira ↗
              </a>
              {t.demanda && <span className="hint">ligado à demanda {t.demanda}</span>}
            </div>
          ) : !jiraConfigurado ? (
            <div className="note">
              Configure o Jira em <Link to="/configuracoes">Configurações</Link> para publicar este bug.
            </div>
          ) : (
            <>
              <div className="form-grid">
                <div className="field">
                  <label htmlFor="tDemanda">
                    Demanda testada <span className="hint">(opcional, ex.: DEV-1)</span>
                  </label>
                  <input id="tDemanda" className="input" value={demanda} onChange={(e) => setDemanda(e.target.value)} placeholder="DEV-1" />
                </div>
              </div>
              <div className="btn-row" style={{ marginTop: 12 }}>
                <button className="btn primary" type="button" onClick={publicarNoJira} disabled={publicar.isPending || salvar.isPending || !titulo.trim()}>
                  {publicar.isPending ? 'Publicando…' : 'Publicar no Jira'}
                </button>
                <span className="hint">Salva a revisão e cria um Bug{demanda ? ` ligado a ${demanda.toUpperCase()}` : ''}.</span>
              </div>
            </>
          )}
          {publicar.data?.aviso && <div className="note" role="alert" style={{ marginTop: 10 }}>{publicar.data.aviso}</div>}
          {publicar.error && (
            <div className="note" role="alert" style={{ marginTop: 10, color: 'var(--red)' }}>
              {publicar.error.message}
            </div>
          )}
        </article>
      )}
    </div>
  )
}
