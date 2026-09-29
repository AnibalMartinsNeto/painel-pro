import { useState } from 'react'
import { AvisoProjeto, Carregando, NotaEtapa } from '../../components/Estado'
import { useProjetoAtual } from '../projetos/ProjetoAtual'
import type { ProjetoDetalhe } from '../projetos/api'

export function ExecucoesPage() {
  const { detalhe } = useProjetoAtual()

  return (
    <>
      <header className="page-head">
        <div>
          <h1>Execuções</h1>
          <p>
            Projeto: <code>{detalhe.data?.nome ?? '…'}</code>
          </p>
        </div>
        <span className="pill idle">Sem execuções</span>
      </header>
      <AvisoProjeto />
      {detalhe.isPending && <Carregando />}
      {/* key: ao trocar de projeto, o formulário é recriado do zero */}
      {detalhe.data && <NovaExecucao key={detalhe.data.id} projeto={detalhe.data} />}

      <section className="card">
        <div className="card-head">
          <span className="eyebrow">Console</span>
        </div>
        <div className="console" tabIndex={0} aria-label="Saída da execução">
          <span className="dim">Nenhuma execução nesta sessão. Selecione os specs acima e clique em Executar.</span>
        </div>
      </section>

      <section className="card flat">
        <div className="card-head" style={{ padding: '16px 16px 0' }}>
          <span className="eyebrow">Histórico</span>
          <span className="hint">0 execuções</span>
        </div>
        <div className="list" style={{ marginTop: 8 }}>
          <div className="empty">Nenhuma execução registrada ainda.</div>
        </div>
      </section>
    </>
  )
}

/**
 * Formulário de nova execução. O estado (specs marcados, navegador...) é
 * local deste componente: useState guarda o valor e, a cada mudança, o
 * React redesenha só o que depende dele.
 */
function NovaExecucao({ projeto }: { projeto: ProjetoDetalhe }) {
  const isK6 = projeto.tipo === 'K6'
  const inicial = projeto.scripts[0]?.specs ?? projeto.specs.slice(0, 1)
  const [selecionados, setSelecionados] = useState<Set<string>>(() => new Set(inicial))
  const [navegador, setNavegador] = useState(projeto.navegadores[0] ?? '')
  const [retentativas, setRetentativas] = useState(0)
  const [abrirNavegador, setAbrirNavegador] = useState(false)

  const escolher = (specs: string[], nav?: string | null) => {
    setSelecionados(new Set(specs))
    if (nav) setNavegador(nav)
  }
  const alternar = (spec: string) =>
    setSelecionados((atual) => {
      const novo = new Set(atual)
      if (novo.has(spec)) novo.delete(spec)
      else novo.add(spec)
      return novo
    })

  const n = selecionados.size
  return (
    <section className="card">
      <div className="card-head">
        <span className="eyebrow">Nova execução</span>
        <div className="seg" role="group" aria-label="Seleção rápida">
          {projeto.scripts.map((s) => (
            <button key={s.nome} type="button" onClick={() => escolher(s.specs, s.navegador)}>
              {s.nome}
            </button>
          ))}
          <button type="button" onClick={() => escolher(projeto.specs)}>
            todos
          </button>
          <button type="button" onClick={() => escolher([])}>
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

      <div className="btn-row" style={{ marginTop: 14 }}>
        <button className="btn primary" type="button" disabled title="A execução pelo painel chega na etapa 4">
          <svg width="12" height="12" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M8 5v14l11-7z" />
          </svg>{' '}
          Executar selecionados
        </button>
        <span className="hint">{n ? `${n} ${n === 1 ? 'spec selecionado' : 'specs selecionados'}` : 'Selecione ao menos um spec'}</span>
      </div>
      <div style={{ marginTop: 14 }}>
        <NotaEtapa etapa={4}>o backend passa a disparar a execução, transmitir o log ao vivo e gravar o resultado no PostgreSQL.</NotaEtapa>
      </div>
    </section>
  )
}
