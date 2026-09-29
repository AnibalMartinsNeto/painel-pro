import { NotaEtapa } from '../../components/Estado'

// Telas das etapas futuras, com o mesmo cabeçalho e estados vazios do
// painel Node. Cada uma ganha sua pasta em features/ quando for construída.

export function TriagemPage() {
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Triagem IA</h1>
          <p>Falhas mais recentes de cada teste. A IA lê o erro e o código do spec e sugere o bug.</p>
        </div>
        <span className="pill idle">Não configurado</span>
      </header>
      <div className="card empty">
        <b>Nenhuma falha registrada</b>Quando um teste falhar ele aparece aqui para triagem.
      </div>
      <NotaEtapa etapa={6}>análise das falhas com IA (Gemini ou Claude) e rascunho do bug editável.</NotaEtapa>
    </>
  )
}

export function AzurePage() {
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Azure DevOps</h1>
          <p>Busque testes por demanda e acompanhe os bugs publicados pelo painel.</p>
        </div>
        <span className="pill idle">Não configurado</span>
      </header>
      <section className="card">
        <span className="eyebrow" style={{ display: 'block', marginBottom: 12 }}>
          Testes por demanda
        </span>
        <div className="btn-row">
          <input className="input" style={{ maxWidth: 200 }} placeholder="Nº da demanda" disabled />
          <button className="btn primary" disabled>
            Buscar
          </button>
        </div>
        <p className="hint" style={{ margin: '10px 0 0' }}>
          O painel procura <code>AB#1234</code>, <code>#1234</code> ou <code>@1234</code> dentro dos specs.
        </p>
      </section>
      <section className="card flat">
        <div className="card-head" style={{ padding: '16px 16px 0' }}>
          <span className="eyebrow">Bugs publicados</span>
          <span className="hint">0</span>
        </div>
        <div className="empty">Nenhum bug publicado ainda.</div>
      </section>
      <NotaEtapa etapa={6}>conexão com organização, projeto e PAT, e publicação de bugs no board.</NotaEtapa>
    </>
  )
}

export function RelatoriosPage() {
  return (
    <>
      <header className="page-head">
        <div>
          <h1>Relatórios</h1>
          <p>Histórico completo das execuções registradas pelo painel.</p>
        </div>
        <div className="btn-row">
          <button className="btn sm" disabled>
            Exportar CSV
          </button>
          <button className="btn sm" disabled>
            Exportar falhas (JSON)
          </button>
        </div>
      </header>
      <div className="stat-strip">
        {['Execuções', 'Testes únicos', 'Já falharam', 'Instáveis', 'Tempo total'].map((r, i) => (
          <div key={r} className={`stat ${i === 2 ? 'r' : i === 3 ? 'a' : ''}`}>
            <b>{i === 4 ? '—' : 0}</b>
            <span>{r}</span>
          </div>
        ))}
      </div>
      <section className="card">
        <div className="card-head">
          <span className="eyebrow">Aprovação por execução</span>
        </div>
        <div className="empty">Sem execuções concluídas.</div>
      </section>
      <NotaEtapa etapa={4}>os números vêm das execuções gravadas no PostgreSQL.</NotaEtapa>
    </>
  )
}
