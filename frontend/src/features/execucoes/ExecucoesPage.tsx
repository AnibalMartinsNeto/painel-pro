import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router'
import { AvisoProjeto, Carregando } from '../../components/Estado'
import { plural } from '../../lib/formato'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import type { ProjetoDetalhe } from '../projetos/api'
import {
  useCancelarExecucao,
  useEmAndamento,
  useExecucoes,
  useIniciarExecucao,
  useLogAoVivo,
  type ExecucaoResumo,
} from './api'
import { ItemExecucao, StatusBadge } from './componentes'

export function ExecucoesPage() {
  const { detalhe } = useProjetoAtual()
  const { data: emAndamento } = useEmAndamento()
  // Guarda a última execução vista para o console continuar mostrando o
  // log depois que ela termina (até começar outra).
  const [acompanhando, setAcompanhando] = useState<ExecucaoResumo | null>(null)
  useEffect(() => {
    if (emAndamento && emAndamento.id !== acompanhando?.id) setAcompanhando(emAndamento)
  }, [emAndamento, acompanhando])

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Execuções</h1>
          <p>
            Projeto: <code>{detalhe.data?.nome ?? '…'}</code>
          </p>
        </div>
        {emAndamento && <span className="pill warn">Executando…</span>}
      </header>
      <AvisoProjeto />
      {detalhe.isPending && <Carregando />}
      {/* key: ao trocar de projeto, o formulário é recriado do zero */}
      {detalhe.data && <NovaExecucao key={detalhe.data.id} projeto={detalhe.data} emAndamento={emAndamento ?? null} />}
      <Console execucao={acompanhando} rodando={!!emAndamento && emAndamento.id === acompanhando?.id} />
      <Historico projeto={detalhe.data?.id ?? ''} />
    </>
  )
}

/**
 * Formulário de nova execução. O estado (specs marcados, navegador...) é
 * local deste componente: useState guarda o valor e, a cada mudança, o
 * React redesenha só o que depende dele.
 */
function NovaExecucao({ projeto, emAndamento }: { projeto: ProjetoDetalhe; emAndamento: ExecucaoResumo | null }) {
  const isK6 = projeto.tipo === 'K6'
  const inicial = projeto.scripts[0]
  // Começa sem nada marcado: o QA escolhe os specs (ou um script) a executar.
  const [script, setScript] = useState<string | null>(null)
  const [selecionados, setSelecionados] = useState<Set<string>>(() => new Set())
  const [navegador, setNavegador] = useState(inicial?.navegador ?? projeto.navegadores[0] ?? '')
  const [retentativas, setRetentativas] = useState(0)
  const [abrirNavegador, setAbrirNavegador] = useState(false)
  const iniciar = useIniciarExecucao()
  const cancelar = useCancelarExecucao()

  const escolher = (nome: string | null, specs: string[], nav?: string | null) => {
    setScript(nome)
    setSelecionados(new Set(specs))
    if (nav) setNavegador(nav)
  }
  const alternar = (spec: string) => {
    setScript(null) // mexeu na seleção: deixa de ser o script exato
    setSelecionados((atual) => {
      const novo = new Set(atual)
      if (novo.has(spec)) novo.delete(spec)
      else novo.add(spec)
      return novo
    })
  }

  const executar = () =>
    iniciar.mutate({
      projeto: projeto.id,
      script,
      // mantém a ordem dos specs do projeto (e não a ordem dos cliques)
      specs: projeto.specs.filter((s) => selecionados.has(s)),
      navegador: isK6 ? null : navegador,
      retentativas,
      abrirNavegador,
    })

  const n = selecionados.size
  return (
    <section className="card">
      <div className="card-head">
        <span className="eyebrow">Nova execução</span>
        <div className="seg" role="group" aria-label="Seleção rápida">
          {projeto.scripts.map((s) => (
            <button key={s.nome} type="button" className={script === s.nome ? 'on' : undefined} onClick={() => escolher(s.nome, s.specs, s.navegador)}>
              {s.nome}
            </button>
          ))}
          <button type="button" onClick={() => escolher(null, projeto.specs)}>
            todos
          </button>
          <button type="button" onClick={() => escolher(null, [])}>
            limpar
          </button>
        </div>
      </div>

      <div className="spec-list">
        {projeto.specs.map((s) => (
          <label key={s} className="check">
            <input type="checkbox" checked={selecionados.has(s)} onChange={() => alternar(s)} />{' '}
            {s.replace(/^(cypress\/e2e|tests)\//, '')}
          </label>
        ))}
      </div>

      <div className="form-grid" style={{ marginTop: 14, gridTemplateColumns: 'repeat(4, 1fr)' }}>
        <div className="field" hidden={isK6}>
          <label htmlFor="fBrowser">Navegador</label>
          <select id="fBrowser" className="input" value={navegador} onChange={(e) => setNavegador(e.target.value)}>
            {projeto.navegadores.map((b) => (
              <option key={b}>{b}</option>
            ))}
          </select>
        </div>
        <div className="field" hidden={isK6}>
          <label htmlFor="fRetries">Retentativas</label>
          <select id="fRetries" className="input" value={retentativas} onChange={(e) => setRetentativas(Number(e.target.value))}>
            {[0, 1, 2, 3].map((r) => (
              <option key={r} value={r}>
                {r}
              </option>
            ))}
          </select>
        </div>
        <div className="field">
          <label htmlFor="fEnv">Ambiente</label>
          <input id="fEnv" className="input" defaultValue="Homologação" />
        </div>
        <div className="field">
          <label>&nbsp;</label>
          <label className="check" style={{ fontFamily: 'inherit' }}>
            <input type="checkbox" checked={abrirNavegador} onChange={(e) => setAbrirNavegador(e.target.checked)} />{' '}
            {isK6 ? 'Abrir navegador (teste de navegador)' : 'Abrir navegador'}
          </label>
        </div>
      </div>
      {isK6 && (
        <p className="hint" style={{ margin: '10px 0 0' }}>
          Carga leve por padrão (máx. 5 usuários virtuais). Para carga real, aponte <code>BASE_URL</code> para um ambiente seu.
        </p>
      )}

      <div className="btn-row" style={{ marginTop: 14 }}>
        {emAndamento ? (
          <>
            <button className="btn danger" type="button" onClick={() => cancelar.mutate(emAndamento.id)} disabled={cancelar.isPending}>
              {cancelar.isPending ? 'Cancelando…' : 'Cancelar execução'}
            </button>
            <span className="hint">Execução #{emAndamento.id} em andamento…</span>
          </>
        ) : (
          <>
            <button className="btn primary" type="button" onClick={executar} disabled={!n || iniciar.isPending || !projeto.instalado}>
              <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
                <path d="M8 5v14l11-7z" />
              </svg>{' '}
              {iniciar.isPending ? 'Iniciando…' : 'Executar selecionados'}
            </button>
            <span className="hint">{n ? plural(n, 'spec selecionado', 'specs selecionados') : 'Selecione ao menos um spec'}</span>
          </>
        )}
        {iniciar.error && (
          <span className="hint" role="alert" style={{ color: 'var(--red)' }}>
            {iniciar.error.message}
          </span>
        )}
      </div>
    </section>
  )
}

/** Console: linhas chegando pelo SSE, com rolagem automática para o fim. */
function Console({ execucao, rodando }: { execucao: ExecucaoResumo | null; rodando: boolean }) {
  const { linhas, statusFinal } = useLogAoVivo(execucao?.id ?? null)
  const caixa = useRef<HTMLDivElement>(null)

  useEffect(() => {
    const c = caixa.current
    if (c) c.scrollTop = c.scrollHeight
  }, [linhas])

  return (
    <section className="card">
      <div className="card-head">
        <span className="eyebrow">Console {rodando && '· ao vivo'}</span>
        {execucao && (
          <div className="btn-row">
            {statusFinal ? <StatusBadge status={statusFinal} /> : rodando && <span className="pill warn">{execucao.script ?? 'Execução personalizada'}</span>}
            {statusFinal && (
              <Link className="btn ghost sm" to={`/execucoes/${execucao.id}`}>
                ver resultado
              </Link>
            )}
          </div>
        )}
      </div>
      <div className="console" ref={caixa} tabIndex={0} aria-label="Saída da execução" role="log">
        {linhas.length ? (
          linhas.map((l, i) => (
            <span key={i} className={classeLinha(l)}>
              {l + '\n'}
            </span>
          ))
        ) : (
          <span className="dim">
            {execucao ? 'Aguardando a saída da ferramenta…' : 'Nenhuma execução nesta sessão. Selecione os specs acima e clique em Executar.'}
          </span>
        )}
      </div>
    </section>
  )
}

// Colore a linha como no painel Node: verde passou, vermelho falhou.
function classeLinha(l: string) {
  if (/✓|✔|\bpassing\b|All specs passed|\bok \d+/i.test(l)) return 'ok'
  if (/✖|✗|✘|\d+\) |failing|\bfailed\b|Error:|AssertionError|level=error/i.test(l)) return 'bad'
  if (/pending|skipped|Retrying|warning|level=warn/i.test(l)) return 'warn'
  if (/^\[painel\]|^[\s─│┌└├┐┘┤=-]+$/.test(l)) return 'dim'
  return undefined
}

/** Lista das execuções gravadas no PostgreSQL (GET /api/execucoes). */
function Historico({ projeto }: { projeto: string }) {
  const { data: execucoes, isPending } = useExecucoes(projeto)

  return (
    <section className="card flat">
      <div className="card-head" style={{ padding: '16px 16px 0' }}>
        <span className="eyebrow">Histórico</span>
        <span className="hint">{plural(execucoes?.length ?? 0, 'execução', 'execuções')}</span>
      </div>
      <div className="list" style={{ marginTop: 8 }}>
        {isPending && <div className="empty">Carregando…</div>}
        {execucoes?.length === 0 && <div className="empty">Nenhuma execução registrada ainda.</div>}
        {execucoes?.map((e) => (
          <ItemExecucao key={e.id} e={e} />
        ))}
      </div>
    </section>
  )
}
